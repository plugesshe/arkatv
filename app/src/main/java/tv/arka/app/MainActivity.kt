package tv.arka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.arka.app.data.Repo
import tv.arka.app.data.Store
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort
import tv.arka.app.ui.AddSourceScreen
import tv.arka.app.ui.ArkaColors
import tv.arka.app.ui.ArkaIcon
import tv.arka.app.ui.ArkaMotion
import tv.arka.app.ui.ArkaRadius
import tv.arka.app.ui.ArkaSize
import tv.arka.app.ui.ArkaTheme
import tv.arka.app.ui.CatalogGridScreen
import tv.arka.app.ui.CatalogGridState
import tv.arka.app.ui.DetailScreen
import tv.arka.app.ui.DiscoverScreen
import tv.arka.app.ui.GridScreen
import tv.arka.app.ui.GuideScreen
import tv.arka.app.ui.HomeScreen
import tv.arka.app.ui.LiveScreen
import tv.arka.app.ui.LocalIsTv
import tv.arka.app.ui.Logo
import tv.arka.app.ui.ProfileScreen
import tv.arka.app.ui.ProfilesScreen
import tv.arka.app.ui.SearchScreen
import tv.arka.app.data.Downloads
import tv.arka.app.data.Recordings
import tv.arka.app.ui.ParentalScreen
import tv.arka.app.ui.FocusSlot
import tv.arka.app.ui.LocalFocusSlot
import tv.arka.app.ui.installImageLoader
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import tv.arka.app.ui.UpdatePrompt
import tv.arka.app.ui.focusHighlight
import tv.arka.app.ui.isTelevision

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Repo.init(applicationContext)
        installImageLoader(applicationContext)
        Downloads.init()
        Recordings.init()
        val television = isTelevision()
        setContent {
            ArkaTheme(isTv = television) {
                Surface(Modifier.fillMaxSize(), color = ArkaColors.Background) {
                    App()
                }
            }
        }
    }
}

/** Main destinations. Handsets show five of them in the bottom bar; TV lists them in the side bar. */
private enum class Destination(val label: String, @DrawableRes val icon: Int) {
    SEARCH("Recherche", R.drawable.ic_arka_recherche),
    HOME("Accueil", R.drawable.ic_arka_accueil),
    MOVIES("Films", R.drawable.ic_arka_films),
    SERIES("Séries", R.drawable.ic_arka_series),
    LIVE("Direct", R.drawable.ic_arka_direct),
    GUIDE("Guide TV", R.drawable.ic_arka_guide_tv),
    MY_LIST("Ma liste", R.drawable.ic_arka_ma_liste),
    DISCOVER("Découvrir", R.drawable.ic_arka_decouverte),
    PROFILE("Mon espace", R.drawable.ic_arka_mon_espace),
}

private val mobileDestinations =
    listOf(Destination.HOME, Destination.LIVE, Destination.GUIDE, Destination.SEARCH, Destination.PROFILE)

private sealed interface Screen {
    data object Main : Screen
    data object AddSource : Screen
    data class Detail(val vod: Vod) : Screen

    data object Discover : Screen
    data object Profiles : Screen
    data object Parental : Screen

    /** Films or series browsed page by page; [state] keeps the category, order, items and scroll position. */
    class Catalog(val title: String, val kind: VodKind, val state: CatalogGridState) : Screen

    data class StaticGrid(val title: String, val items: List<Vod>) : Screen
}

@Composable
private fun App() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Main) }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    // Keeps each screen's saved state (scroll positions, selected filters) while another one is on top.
    val holder = rememberSaveableStateHolder()
    // TV: one focus memory per screen of the stack.
    val tv = LocalIsTv.current
    val slots = remember { HashMap<Int, FocusSlot>() }
    val pop: () -> Unit = {
        if (stack.size > 1) {
            holder.removeState("screen-${stack.lastIndex}")
            slots.remove(stack.lastIndex)
            stack.removeAt(stack.lastIndex)
            slots[stack.lastIndex]?.restore = true
        }
    }

    UpdatePrompt()

    // First launch: the set-up stays on screen until its last step is confirmed.
    var onboarding by remember { mutableStateOf(Repo.sources.isEmpty()) }
    if (onboarding || Repo.sources.isEmpty()) {
        AddSourceScreen(onBack = null, onDone = { onboarding = false })
        return
    }

    // With several profiles, the app opens on "Qui regarde ?".
    if (Repo.profiles.size > 1 && !Repo.profileChosen) {
        ProfilesScreen(onDone = { Repo.profileChosen = true })
        return
    }

    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = stack.size == 1 && destination != Destination.HOME) { destination = Destination.HOME }

    val openVod: (Vod) -> Unit = { stack.add(Screen.Detail(it)) }
    // The screen title is the kind; the category opened from a home row becomes the grid's filter.
    val openCatalog: (String, VodKind, String?, VodSort) -> Unit = { _, kind, categoryId, sort ->
        val title = if (kind == VodKind.MOVIE) "Films" else "Séries"
        stack.add(Screen.Catalog(title, kind, CatalogGridState(sort, categoryId)))
    }

    val index = stack.lastIndex
    val slot = if (tv) slots.getOrPut(index) { FocusSlot() } else null
    holder.SaveableStateProvider("screen-$index") {
        CompositionLocalProvider(LocalFocusSlot provides slot) {
        // The main screen manages its own focus (side bar); the others start on their first element.
        TvInitialFocus(enabled = tv && index > 0) {
        when (val screen = stack[index]) {
            Screen.Main -> MainShell(
                destination = destination,
                onDestination = { destination = it },
                onOpen = openVod,
                onCatalog = openCatalog,
                onList = { title, items -> stack.add(Screen.StaticGrid(title, items)) },
                onAddSource = { stack.add(Screen.AddSource) },
                onDiscover = { stack.add(Screen.Discover) },
                onProfiles = { stack.add(Screen.Profiles) },
                onParental = { stack.add(Screen.Parental) },
            )
            Screen.Discover -> DiscoverScreen(onBack = pop, onOpen = openVod)
            Screen.Profiles -> ProfilesScreen(onDone = pop)
            Screen.Parental -> ParentalScreen(onBack = pop)
            Screen.AddSource -> AddSourceScreen(onBack = pop, onDone = pop)
            is Screen.Detail -> DetailScreen(screen.vod, onBack = pop, onOpen = openVod)
            is Screen.Catalog -> CatalogGridScreen(
                title = screen.title,
                kind = screen.kind,
                state = screen.state,
                onBack = pop,
                onOpen = openVod,
            )
            is Screen.StaticGrid -> GridScreen(screen.title, screen.items, onBack = pop, onOpen = openVod)
        }
        }
        }
    }
}

/**
 * TV: a screen that has just appeared gets the focus on its first element, unless something inside
 * it (a card the user is coming back to) took it first. Content that is still loading is waited for.
 */
@Composable
private fun TvInitialFocus(enabled: Boolean, tick: Int = 0, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
    } else {
        val requester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        val focusedNow by rememberUpdatedState(focused)
        Box(Modifier.fillMaxSize().focusRequester(requester).onFocusChanged { focused = it.hasFocus }) { content() }
        LaunchedEffect(tick) {
            for (attempt in 0 until 20) {
                delay(if (attempt == 0) 50 else 120)
                if (focusedNow) break
                try {
                    requester.requestFocus()
                } catch (e: IllegalStateException) {
                    // Nothing to focus yet.
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    destination: Destination,
    onDestination: (Destination) -> Unit,
    onOpen: (Vod) -> Unit,
    onCatalog: (String, VodKind, String?, VodSort) -> Unit,
    onList: (String, List<Vod>) -> Unit,
    onAddSource: () -> Unit,
    onDiscover: () -> Unit,
    onProfiles: () -> Unit,
    onParental: () -> Unit,
) {
    // The film and series grids opened from the TV side bar keep their state while the user moves around.
    val movies = remember { CatalogGridState() }
    val series = remember { CatalogGridState() }
    val goHome = { onDestination(Destination.HOME) }

    val content: @Composable () -> Unit = {
        when (destination) {
            Destination.HOME -> HomeScreen(onOpen, onCatalog, onDiscover, onProfiles)
            Destination.LIVE -> LiveScreen()
            Destination.GUIDE -> GuideScreen()
            Destination.SEARCH -> SearchScreen(onOpen = onOpen, onCatalog = onCatalog)
            Destination.PROFILE -> ProfileScreen(onAddSource, onOpen, onList, onProfiles, onParental)
            Destination.MOVIES -> CatalogGridScreen("Films", VodKind.MOVIE, movies, onBack = goHome, onOpen = onOpen)
            Destination.SERIES -> CatalogGridScreen("Séries", VodKind.SERIES, series, onBack = goHome, onOpen = onOpen)
            Destination.DISCOVER -> DiscoverScreen(onBack = goHome, onOpen = onOpen)
            Destination.MY_LIST -> GridScreen("Ma liste", Repo.myList, onBack = goHome, onOpen = onOpen)
        }
    }

    if (LocalIsTv.current) {
        TvShell(destination, onDestination, content)
    } else {
        Scaffold(
            containerColor = ArkaColors.Background,
            bottomBar = { BottomBar(destination, onDestination) },
        ) { padding ->
            // The home hero runs under the status bar; other screens start below it.
            val top = if (destination == Destination.HOME) 0.dp else padding.calculateTopPadding()
            Box(Modifier.fillMaxSize().padding(top = top, bottom = padding.calculateBottomPadding())) {
                content()
                ConnectionBanner(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Handset navigation: 64 dp bar, five destinations, the active one marked by a pill and a bold label. */
@Composable
private fun BottomBar(current: Destination, onSelect: (Destination) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ArkaColors.BackgroundAlt).navigationBarsPadding().height(ArkaSize.bottomBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mobileDestinations.forEach { item ->
            val selected = item == current
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(width = 56.dp, height = 30.dp)
                        .clip(RoundedCornerShape(ArkaRadius.pill))
                        .background(if (selected) ArkaColors.SurfaceHigh else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    ArkaIcon(
                        item.icon,
                        contentDescription = null,
                        tint = if (selected) ArkaColors.RedBright else ArkaColors.TextSecondary,
                        size = 22.dp,
                    )
                }
                Text(
                    item.label,
                    color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight(700) else FontWeight(500),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * TV navigation: a side bar that takes little room when idle and opens when the focus reaches it.
 * The content is dimmed while the bar is open and never moves.
 */
@Composable
private fun TvShell(current: Destination, onSelect: (Destination) -> Unit, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Incremented each time the content must take the focus: at start and after a choice in the bar.
    var enterTick by remember { mutableIntStateOf(0) }
    var contentFocused by remember { mutableStateOf(false) }
    val currentItem = remember { FocusRequester() }
    // Back inside the content opens the side bar on the current section; Back in the bar then
    // returns to the home screen, and from there leaves the app.
    BackHandler(enabled = contentFocused) {
        try {
            currentItem.requestFocus()
        } catch (e: IllegalStateException) {
            // The bar is always laid out; nothing else to do.
        }
    }
    val width by animateDpAsState(
        targetValue = if (expanded) ArkaSize.tvSidebarExpanded else ArkaSize.tvSidebarCollapsed,
        animationSpec = tween(ArkaMotion.FOCUS_MS, easing = ArkaMotion.easing),
        label = "sidebar",
    )
    val items = Destination.entries.filter { item ->
        when (item) {
            Destination.MOVIES -> Repo.movieCount > 0
            Destination.SERIES -> Repo.seriesCount > 0
            Destination.DISCOVER -> Repo.movieCount > 0 || Repo.seriesCount > 0
            else -> true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().padding(start = ArkaSize.tvSidebarCollapsed)
                .onFocusChanged { contentFocused = it.hasFocus }
        ) {
            TvInitialFocus(enabled = true, tick = enterTick, content = content)
            ConnectionBanner(Modifier.align(Alignment.TopEnd))
        }
        if (expanded) {
            Box(Modifier.fillMaxSize().background(ArkaColors.Scrim.copy(alpha = 0.4f)))
        }
        Column(
            Modifier.fillMaxHeight().width(width)
                .background(if (expanded) ArkaColors.BackgroundAlt else ArkaColors.Background)
                .onFocusChanged { expanded = it.hasFocus }
                .padding(vertical = 20.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.height(32.dp).padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
                Logo(wordmark = expanded, height = 20.dp)
            }
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                val selected = item == current
                Row(
                    Modifier.fillMaxWidth().height(ArkaSize.tvControlMin)
                        .then(if (selected) Modifier.focusRequester(currentItem) else Modifier)
                        .focusHighlight(ArkaRadius.sm, 1f)
                        .clip(RoundedCornerShape(ArkaRadius.sm))
                        .background(if (selected) ArkaColors.SurfaceHigh else Color.Transparent)
                        .clickable {
                            // OK chooses the section and goes straight into it.
                            onSelect(item)
                            enterTick++
                        }
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArkaIcon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) ArkaColors.RedBright else ArkaColors.TextSecondary,
                        size = 18.dp,
                    )
                    if (expanded) {
                        Text(
                            item.label,
                            color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
                            style = ArkaTheme.type.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Says when the catalogue on screen comes from the cache because the source could not be reached,
 * or when a refresh is running behind the content. Hidden the rest of the time.
 */
@Composable
private fun ConnectionBanner(modifier: Modifier) {
    val problem = Repo.syncError
    val refreshing = Repo.refreshing
    if (problem == null && !refreshing) return
    Row(
        modifier.padding(horizontal = ArkaTheme.margin, vertical = 8.dp)
            .clip(RoundedCornerShape(ArkaRadius.pill))
            .background(ArkaColors.SurfaceHigh)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (refreshing) {
            Text("Actualisation en arrière-plan…", color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp))
        } else {
            ArkaIcon(R.drawable.ic_arka_hors_ligne, null, tint = ArkaColors.Warning, size = 16.dp)
            Text(
                "Hors connexion · catalogue en cache",
                color = ArkaColors.TextPrimary,
                style = ArkaTheme.type.label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            Text(
                "Réessayer",
                color = ArkaColors.TextPrimary,
                style = ArkaTheme.type.label,
                fontWeight = FontWeight(700),
                modifier = Modifier.focusHighlight(ArkaRadius.sm, 1f).clip(RoundedCornerShape(ArkaRadius.pill)).clickable { Repo.reload() }.padding(8.dp),
            )
        }
    }
}
