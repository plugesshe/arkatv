package tv.arka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import tv.arka.app.data.Repo
import tv.arka.app.data.Store
import tv.arka.app.data.Vod
import tv.arka.app.ui.AddSourceScreen
import tv.arka.app.ui.ArkaBg
import tv.arka.app.ui.ArkaMuted
import tv.arka.app.ui.ArkaRed
import tv.arka.app.ui.ArkaSurface
import tv.arka.app.ui.ArkaTheme
import tv.arka.app.ui.DetailScreen
import tv.arka.app.ui.GridScreen
import tv.arka.app.ui.GuideScreen
import tv.arka.app.ui.HomeScreen
import tv.arka.app.ui.LiveScreen
import tv.arka.app.ui.ProfileScreen
import tv.arka.app.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Repo.init()
        setContent {
            ArkaTheme {
                Surface(Modifier.fillMaxSize(), color = ArkaBg) {
                    App()
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Main : Screen
    data object AddSource : Screen
    data class Detail(val vod: Vod) : Screen
    data class Grid(val title: String, val items: List<Vod>) : Screen
}

@Composable
private fun App() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Main) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pop = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    if (Repo.sources.isEmpty()) {
        AddSourceScreen(onBack = null, onDone = {})
        return
    }

    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = stack.size == 1 && tab != 0) { tab = 0 }

    val openVod: (Vod) -> Unit = { stack.add(Screen.Detail(it)) }

    when (val screen = stack.last()) {
        Screen.Main -> MainTabs(
            tab = tab,
            onTab = { tab = it },
            onOpen = openVod,
            onGrid = { title, items -> stack.add(Screen.Grid(title, items)) },
            onAddSource = { stack.add(Screen.AddSource) },
        )
        Screen.AddSource -> AddSourceScreen(onBack = { pop() }, onDone = { pop() })
        is Screen.Detail -> DetailScreen(screen.vod, onBack = { pop() })
        is Screen.Grid -> GridScreen(screen.title, screen.items, onBack = { pop() }, onOpen = openVod)
    }
}

@Composable
private fun MainTabs(
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (Vod) -> Unit,
    onGrid: (String, List<Vod>) -> Unit,
    onAddSource: () -> Unit,
) {
    val tabs = listOf(
        "Accueil" to Icons.Filled.Home,
        "Direct" to Icons.Filled.LiveTv,
        "Guide" to Icons.Filled.CalendarMonth,
        "Recherche" to Icons.Filled.Search,
        "Mon espace" to Icons.Filled.Person,
    )
    Scaffold(
        containerColor = ArkaBg,
        bottomBar = {
            NavigationBar(containerColor = ArkaSurface) {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { onTab(index) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ArkaRed,
                            selectedTextColor = ArkaRed,
                            unselectedIconColor = ArkaMuted,
                            unselectedTextColor = ArkaMuted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(onOpen = onOpen, onGrid = onGrid)
                1 -> LiveScreen()
                2 -> GuideScreen()
                3 -> SearchScreen(onOpen = onOpen)
                else -> ProfileScreen(onAddSource = onAddSource, onOpen = onOpen, onGrid = onGrid)
            }
        }
    }
}
