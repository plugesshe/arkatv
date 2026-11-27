package tv.arka.app.data.db

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.arka.app.data.Catalog
import tv.arka.app.data.Category
import tv.arka.app.data.Channel
import tv.arka.app.data.Program
import tv.arka.app.data.Source
import tv.arka.app.data.TextNormalizer
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort
import tv.arka.app.data.XtreamApi
import tv.arka.app.data.security.SecretBox

/** Persists the last valid catalogue and guide of each source, and reads them back at start-up. */
class CatalogStore(private val database: ArkaDatabase) {
    private val dao = database.catalog()

    /**
     * Receives a catalogue entry by entry and writes it in batches, so a download of any size
     * only ever holds one batch in memory.
     */
    inner class Writer internal constructor(private val source: Source) {
        private val xtream = source.type == "xtream"
        private val channels = ArrayList<ChannelRow>(BATCH)
        private val vods = ArrayList<VodRow>(BATCH)
        private var channelPos = 0
        private var moviePos = 0
        private var seriesPos = 0

        fun liveCategories(list: List<Category>) = categories(LIVE, list)
        fun movieCategories(list: List<Category>) = categories(MOVIE, list)
        fun seriesCategories(list: List<Category>) = categories(SERIES, list)

        private fun categories(kind: String, list: List<Category>) {
            dao.insertCategoriesNow(list.mapIndexed { index, c -> CategoryRow(source.id, kind, index, c.id, c.name) })
        }

        fun channel(c: Channel) {
            channels.add(
                ChannelRow(
                    sourceId = source.id,
                    pos = channelPos++,
                    streamId = if (xtream) c.id else "",
                    secret = if (xtream) "" else SecretBox.encrypt(c.url),
                    name = c.name,
                    logo = c.logo,
                    categoryId = c.categoryId,
                    epgId = c.epgId,
                    number = c.number,
                    search = TextNormalizer.normalize(c.name),
                )
            )
            if (channels.size >= BATCH) flushChannels()
        }

        fun vod(v: Vod) {
            val movie = v.kind == VodKind.MOVIE
            vods.add(
                VodRow(
                    sourceId = source.id,
                    kind = if (movie) MOVIE else SERIES,
                    pos = if (movie) moviePos++ else seriesPos++,
                    streamId = if (xtream) v.id else "",
                    secret = if (xtream) "" else SecretBox.encrypt(v.url),
                    ext = if (xtream) v.url.substringAfterLast('.', "") else "",
                    name = v.name,
                    poster = v.poster,
                    categoryId = v.categoryId,
                    rating = v.rating,
                    addedAt = v.added,
                    plot = v.plot,
                    year = v.year,
                    search = TextNormalizer.normalize(v.name),
                )
            )
            if (vods.size >= BATCH) flushVods()
        }

        private fun flushChannels() {
            if (channels.isNotEmpty()) {
                dao.insertChannelsNow(ArrayList(channels))
                channels.clear()
            }
        }

        private fun flushVods() {
            if (vods.isNotEmpty()) {
                dao.insertVodsNow(ArrayList(vods))
                vods.clear()
            }
        }

        internal fun finish() {
            flushChannels()
            flushVods()
        }
    }

    /**
     * Replaces the stored catalogue of [source] with whatever [fill] writes, in one transaction.
     * If [fill] throws (network cut, invalid answer), nothing changes: the previous catalogue stays.
     * Blocking: call it from a background thread.
     */
    fun replaceBlocking(source: Source, fill: (Writer) -> Unit) {
        database.runInTransaction(
            Runnable {
                dao.deleteChannelsNow(source.id)
                dao.deleteVodsNow(source.id)
                dao.deleteCategoriesNow(source.id)
                val writer = Writer(source)
                fill(writer)
                writer.finish()
            }
        )
    }

    suspend fun replace(source: Source, fill: (Writer) -> Unit) =
        withContext(Dispatchers.IO) { replaceBlocking(source, fill) }

    suspend fun save(source: Source, catalog: Catalog) = replace(source) { writer ->
        writer.liveCategories(catalog.liveCategories)
        writer.movieCategories(catalog.vodCategories)
        writer.seriesCategories(catalog.seriesCategories)
        catalog.channels.forEach(writer::channel)
        catalog.movies.forEach(writer::vod)
        catalog.series.forEach(writer::vod)
    }

    suspend fun count(source: Source, kind: VodKind): Int =
        dao.countVods(source.id, if (kind == VodKind.MOVIE) MOVIE else SERIES)

    /** Returns null when nothing was ever cached for this source. */
    suspend fun load(source: Source, liveFormat: String): Catalog? {
        val channelRows = dao.channels(source.id)
        val movieRows = dao.vods(source.id, MOVIE)
        val seriesRows = dao.vods(source.id, SERIES)
        if (channelRows.isEmpty() && movieRows.isEmpty() && seriesRows.isEmpty()) return null
        val categories = dao.categories(source.id).groupBy { it.kind }
        val api = if (source.type == "xtream") XtreamApi(source) else null

        val channels = channelRows.map { channelOf(it, api, liveFormat) }
        fun vods(rows: List<VodRow>, kind: VodKind): List<Vod> = rows.map { vodOf(it, kind, api) }
        fun cats(kind: String): List<Category> =
            categories[kind].orEmpty().map { Category(it.categoryId, it.name) }

        return Catalog(
            liveCategories = cats(LIVE),
            channels = channels,
            vodCategories = cats(MOVIE),
            movies = vods(movieRows, VodKind.MOVIE),
            seriesCategories = cats(SERIES),
            series = vods(seriesRows, VodKind.SERIES),
        )
    }

    private fun channelOf(row: ChannelRow, api: XtreamApi?, liveFormat: String): Channel {
        val url = api?.liveUrl(row.streamId, liveFormat) ?: SecretBox.decrypt(row.secret).orEmpty()
        return Channel(
            id = if (api != null) row.streamId else url,
            name = row.name,
            logo = row.logo,
            categoryId = row.categoryId,
            epgId = row.epgId,
            url = url,
            number = row.number,
        )
    }

    private fun vodOf(row: VodRow, kind: VodKind, api: XtreamApi?): Vod {
        val url = if (api == null) {
            SecretBox.decrypt(row.secret).orEmpty()
        } else if (kind == VodKind.MOVIE) {
            api.movieUrl(row.streamId, row.ext.ifBlank { "mp4" })
        } else {
            ""
        }
        return Vod(
            id = if (api != null) row.streamId else url,
            kind = kind,
            name = row.name,
            poster = row.poster,
            categoryId = row.categoryId,
            url = url,
            rating = row.rating,
            added = row.addedAt,
            plot = row.plot,
            year = row.year,
        )
    }

    private fun apiOf(source: Source): XtreamApi? = if (source.type == "xtream") XtreamApi(source) else null

    /** [normalized] must come from [TextNormalizer.normalize]. Best matches first. */
    suspend fun searchChannels(
        source: Source,
        liveFormat: String,
        normalized: String,
        limit: Int,
        offset: Int,
    ): List<Channel> {
        val query = searchQuery("channels", source.id, null, normalized, limit, offset) ?: return emptyList()
        val api = apiOf(source)
        return dao.searchChannels(query).map { channelOf(it, api, liveFormat) }
    }

    suspend fun searchVods(source: Source, kind: VodKind, normalized: String, limit: Int, offset: Int): List<Vod> {
        val kindName = if (kind == VodKind.MOVIE) MOVIE else SERIES
        val query = searchQuery("vods", source.id, kindName, normalized, limit, offset) ?: return emptyList()
        val api = apiOf(source)
        return dao.queryVods(query).map { vodOf(it, kind, api) }
    }

    /** What the interface keeps in memory: channels, categories and a few rows. Films and series stay in the database. */
    class Summary(
        val liveCategories: List<Category>,
        val channels: List<Channel>,
        val vodCategories: List<Category>,
        val seriesCategories: List<Category>,
        val movieCount: Int,
        val seriesCount: Int,
        val latestMovies: List<Vod>,
        val latestSeries: List<Vod>,
    )

    /** Returns null when nothing was ever cached for this source. Empty categories are left out. */
    suspend fun loadSummary(source: Source, liveFormat: String, rowSize: Int): Summary? {
        val channelRows = dao.channels(source.id)
        val movieCount = dao.countVods(source.id, MOVIE)
        val seriesCount = dao.countVods(source.id, SERIES)
        if (channelRows.isEmpty() && movieCount == 0 && seriesCount == 0) return null
        val categories = dao.categories(source.id).groupBy { it.kind }
        val api = apiOf(source)
        fun cats(kind: String, used: Set<String>?): List<Category> = categories[kind].orEmpty()
            .filter { used == null || it.categoryId in used }
            .map { Category(it.categoryId, it.name) }
        return Summary(
            liveCategories = cats(LIVE, null),
            channels = channelRows.map { channelOf(it, api, liveFormat) },
            vodCategories = cats(MOVIE, dao.usedCategories(source.id, MOVIE).toSet()),
            seriesCategories = cats(SERIES, dao.usedCategories(source.id, SERIES).toSet()),
            movieCount = movieCount,
            seriesCount = seriesCount,
            latestMovies = vodPage(source, VodKind.MOVIE, null, VodSort.RECENT, rowSize, 0),
            latestSeries = vodPage(source, VodKind.SERIES, null, VodSort.RECENT, rowSize, 0),
        )
    }

    /** One page of films or series, optionally limited to a category. */
    suspend fun vodPage(
        source: Source,
        kind: VodKind,
        categoryId: String?,
        sort: VodSort,
        limit: Int,
        offset: Int,
    ): List<Vod> {
        val sql = StringBuilder("SELECT * FROM vods WHERE sourceId = ? AND kind = ?")
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(if (kind == VodKind.MOVIE) MOVIE else SERIES)
        if (categoryId != null) {
            sql.append(" AND categoryId = ?")
            args.add(categoryId)
        }
        val order = when (sort) {
            VodSort.PROVIDER -> "pos"
            VodSort.NAME -> "search, pos"
            VodSort.RECENT -> "addedAt DESC, pos"
            VodSort.YEAR -> "year DESC, pos"
            VodSort.RATING -> "CAST(rating AS REAL) DESC, pos"
        }
        sql.append(" ORDER BY ").append(order).append(" LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)
        val api = apiOf(source)
        return dao.queryVods(SimpleSQLiteQuery(sql.toString(), args.toTypedArray())).map { vodOf(it, kind, api) }
    }

    /**
     * Every term must appear in the name. Ranking: exact name, then names starting with the query,
     * then names containing it at the start of a word, then the rest; shorter names first.
     */
    private fun searchQuery(
        table: String,
        sourceId: String,
        kind: String?,
        normalized: String,
        limit: Int,
        offset: Int,
    ): SimpleSQLiteQuery? {
        val terms = TextNormalizer.terms(normalized)
        if (terms.isEmpty()) return null
        val sql = StringBuilder("SELECT * FROM ").append(table).append(" WHERE sourceId = ?")
        val args = ArrayList<Any>()
        args.add(sourceId)
        if (kind != null) {
            sql.append(" AND kind = ?")
            args.add(kind)
        }
        for (term in terms) {
            sql.append(" AND search LIKE ?")
            args.add("%$term%")
        }
        sql.append(
            " ORDER BY CASE WHEN search = ? THEN 0 WHEN search LIKE ? THEN 1 WHEN search LIKE ? THEN 2 ELSE 3 END," +
                " length(search), pos LIMIT ? OFFSET ?"
        )
        args.add(normalized)
        args.add("$normalized%")
        args.add("% $normalized%")
        args.add(limit)
        args.add(offset)
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    suspend fun saveEpg(sourceId: String, guide: Map<String, List<Program>>) {
        val rows = ArrayList<ProgramRow>()
        for ((channel, programs) in guide) {
            // The primary key is (source, channel, start): keep one programme per start time.
            programs.distinctBy { it.start }.forEach { p ->
                rows.add(ProgramRow(sourceId, channel, p.start, p.end, p.title, p.desc))
            }
        }
        database.withTransaction {
            dao.deletePrograms(sourceId)
            dao.insertPrograms(rows)
        }
    }

    suspend fun loadEpg(sourceId: String, from: Long): Map<String, List<Program>> {
        val result = HashMap<String, MutableList<Program>>()
        for (row in dao.programs(sourceId, from)) {
            result.getOrPut(row.channel) { ArrayList() }
                .add(Program(row.startAt, row.endAt, row.title, row.description))
        }
        return result
    }

    suspend fun delete(sourceId: String) {
        database.withTransaction {
            dao.deleteChannels(sourceId)
            dao.deleteVods(sourceId)
            dao.deleteCategories(sourceId)
            dao.deletePrograms(sourceId)
        }
    }

    private companion object {
        const val LIVE = "live"
        const val MOVIE = "movie"
        const val SERIES = "series"
        const val BATCH = 500
    }
}
