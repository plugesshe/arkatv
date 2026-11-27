package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.arka.app.data.Channel
import tv.arka.app.ui.CATEGORY_ALL
import tv.arka.app.ui.CATEGORY_FAVORITES
import tv.arka.app.ui.CATEGORY_RECENT
import tv.arka.app.ui.channelsFor

class LiveFilterTest {
    private fun channel(id: String, category: String) = Channel(id, "Chaîne $id", "", category, "", "url/$id", id.toInt())

    private val channels = listOf(channel("1", "news"), channel("2", "news"), channel("3", "sport"), channel("4", "adult"))

    private fun ids(
        category: String,
        favorites: List<String> = emptyList(),
        recent: List<String> = emptyList(),
        hiddenCategories: Set<String> = emptySet(),
        hiddenChannels: Set<String> = emptySet(),
    ) = channelsFor(category, channels, favorites, recent, hiddenCategories, hiddenChannels).map { it.id }

    @Test
    fun showsEverythingByDefault() {
        assertEquals(listOf("1", "2", "3", "4"), ids(CATEGORY_ALL))
        assertEquals(listOf("1", "2"), ids("news"))
    }

    @Test
    fun hiddenGroupsLeaveTheFullListButStayReachable() {
        assertEquals(listOf("1", "2", "3"), ids(CATEGORY_ALL, hiddenCategories = setOf("adult")))
        assertEquals(listOf("4"), ids("adult", hiddenCategories = setOf("adult")))
    }

    @Test
    fun hiddenChannelsDisappearEverywhere() {
        val hidden = setOf("2")
        assertEquals(listOf("1", "3", "4"), ids(CATEGORY_ALL, hiddenChannels = hidden))
        assertEquals(listOf("1"), ids("news", hiddenChannels = hidden))
        assertEquals(listOf("3"), ids(CATEGORY_FAVORITES, favorites = listOf("2", "3"), hiddenChannels = hidden))
    }

    @Test
    fun favouritesAndRecentChannelsKeepTheUsersOrder() {
        assertEquals(listOf("3", "1"), ids(CATEGORY_FAVORITES, favorites = listOf("3", "1", "99")))
        assertEquals(listOf("4", "2"), ids(CATEGORY_RECENT, recent = listOf("4", "2")))
    }
}
