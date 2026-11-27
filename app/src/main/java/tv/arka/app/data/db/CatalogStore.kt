package tv.arka.app.data.db

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.arka.app.data.Catalog
import tv.arka.app.data.Category
import tv.arka.app.data.Channel
import tv.arka.app.data.Program
import tv.arka.app.data.SmartCounts
import tv.arka.app.data.Source
import tv.arka.app.data.TextNormalizer
import tv.arka.app.data.Vod
import tv.arka.app.data.TitleCleaner
import tv.arka.app.data.Tmdb
import tv.arka.app.data.VodFilter
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
        // Shown without the provider's tags; the search still runs on the stored name.
        val clean = TitleCleaner.clean(row.name)
        return Vod(
            tags = clean.tags,
            id = if (api != null) row.streamId else url,
            kind = kind,
            name = clean.title,
            poster = row.poster,
            categoryId = row.categoryId,
            url = url,
            rating = row.rating,
            added = row.addedAt,
            plot = row.plot,
            year = row.year.ifBlank { clean.year },
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
        val movieCategoryCounts: Map<String, Int>,
        val seriesCategoryCounts: Map<String, Int>,
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
        val movieCounts = dao.categoryCounts(source.id, MOVIE).associate { it.categoryId to it.total }
        val seriesCounts = dao.categoryCounts(source.id, SERIES).associate { it.categoryId to it.total }
        fun cats(kind: String, used: Set<String>?): List<Category> = categories[kind].orEmpty()
            .filter { used == null || it.categoryId in used }
            .map { Category(it.categoryId, it.name) }
        return Summary(
            liveCategories = cats(LIVE, null),
            channels = channelRows.map { channelOf(it, api, liveFormat) },
            vodCategories = cats(MOVIE, movieCounts.keys),
            seriesCategories = cats(SERIES, seriesCounts.keys),
            movieCount = movieCount,
            seriesCount = seriesCount,
            movieCategoryCounts = movieCounts,
            seriesCategoryCounts = seriesCounts,
            latestMovies = vodPage(source, VodKind.MOVIE, null, VodSort.RECENT, null, rowSize, 0),
            latestSeries = vodPage(source, VodKind.SERIES, null, VodSort.RECENT, null, rowSize, 0),
        )
    }

    /**
     * One page of films or series, optionally limited to a category.
     * [descending] null means the natural direction of the order: A → Z for names, newest or best first otherwise.
     */
    suspend fun vodPage(
        source: Source,
        kind: VodKind,
        categoryId: String?,
        sort: VodSort,
        descending: Boolean?,
        limit: Int,
        offset: Int,
        filter: VodFilter = VodFilter.NONE,
    ): List<Vod> {
        val sql = StringBuilder("SELECT * FROM vods WHERE sourceId = ? AND kind = ?")
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        if (categoryId != null) {
            sql.append(" AND categoryId = ?")
            args.add(categoryId)
        }
        lockFilter(sql, args, kindName(kind))
        versionFilter(sql, args, filter, "name")
        if (filter.minRating > 0) {
            sql.append(" AND CAST(rating AS REAL) >= ?")
            args.add(filter.minRating)
        }
        if (filter.yearFrom > 0 || filter.yearTo > 0) {
            // Titles without a year never match a year range.
            sql.append(" AND CAST(year AS INTEGER) BETWEEN ? AND ?")
            args.add(if (filter.yearFrom > 0) filter.yearFrom else 1)
            args.add(if (filter.yearTo > 0) filter.yearTo else 9999)
        }
        val excluded = filter.excludeIds.take(VodFilter.MAX_EXCLUDED)
        if (excluded.isNotEmpty()) {
            sql.append(" AND streamId NOT IN (").append(excluded.joinToString(",") { "?" }).append(")")
            args.addAll(excluded)
        }
        val down = descending ?: (sort != VodSort.NAME && sort != VodSort.PROVIDER)
        val direction = if (down) "DESC" else "ASC"
        val order = when (sort) {
            VodSort.PROVIDER -> "pos $direction"
            VodSort.NAME -> "search $direction, pos"
            VodSort.RECENT -> "addedAt $direction, pos"
            VodSort.YEAR -> "year $direction, pos"
            VodSort.RATING -> "CAST(rating AS REAL) $direction, pos"
            VodSort.POPULAR -> "pos $direction"
        }
        sql.append(" ORDER BY ").append(order).append(" LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)
        val api = apiOf(source)
        return dao.queryVods(SimpleSQLiteQuery(sql.toString(), args.toTypedArray())).map { vodOf(it, kind, api) }
    }

    private fun kindName(kind: VodKind): String = if (kind == VodKind.MOVIE) MOVIE else SERIES

    /**
     * Category ids to leave out of every query, by kind ([LIVE], [MOVIE], [SERIES]).
     * Set while a child profile is active; empty otherwise.
     */
    @Volatile
    var locked: Map<String, Set<String>> = emptyMap()

    /** Language and quality as the provider writes them in its titles; [column] is the name column. */
    private fun versionFilter(sql: StringBuilder, args: MutableList<Any>, filter: VodFilter, column: String) {
        val language = when (filter.language) {
            "FR" -> listOf("FR %", "FR-%", "%VF%", "%FRENCH%")
            "VOSTFR" -> listOf("%VOSTFR%")
            "MULTI" -> listOf("%MULTI%")
            else -> emptyList()
        }
        val quality = when (filter.quality) {
            "4K" -> listOf("%4K%", "%UHD%")
            "FHD" -> listOf("%FHD%", "%1080%")
            "HD" -> listOf("HD %", "% HD %", "% HD", "%[HD]%", "%720%")
            else -> emptyList()
        }
        for (patterns in listOf(language, quality)) {
            if (patterns.isEmpty()) continue
            sql.append(" AND (").append(patterns.joinToString(" OR ") { "$column LIKE ?" }).append(")")
            args.addAll(patterns)
        }
    }

    private fun lockFilter(sql: StringBuilder, args: MutableList<Any>, kind: String) {
        val hidden = locked[kind].orEmpty().take(MAX_LOCKED)
        if (hidden.isEmpty()) return
        sql.append(" AND categoryId NOT IN (").append(hidden.joinToString(",") { "?" }).append(")")
        args.addAll(hidden)
    }

    private fun categoryFilter(sql: StringBuilder, args: MutableList<Any>, categories: Collection<String>) {
        val wanted = categories.take(MAX_CATEGORY_FILTER)
        if (wanted.isEmpty()) return
        sql.append(" AND categoryId IN (").append(wanted.joinToString(",") { "?" }).append(")")
        args.addAll(wanted)
    }

    /** How many films or series match the Discovery choices (all categories when none is chosen). */
    suspend fun countIn(source: Source, kind: VodKind, categories: Collection<String>): Int {
        val sql = StringBuilder("SELECT COUNT(*) FROM vods WHERE sourceId = ? AND kind = ?")
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        categoryFilter(sql, args, categories)
        lockFilter(sql, args, kindName(kind))
        return dao.queryInt(SimpleSQLiteQuery(sql.toString(), args.toTypedArray()))
    }

    /** The [position]-th match (provider order) of the Discovery choices, or null when out of range. */
    suspend fun pickIn(source: Source, kind: VodKind, categories: Collection<String>, position: Int): Vod? {
        val sql = StringBuilder("SELECT * FROM vods WHERE sourceId = ? AND kind = ?")
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        categoryFilter(sql, args, categories)
        lockFilter(sql, args, kindName(kind))
        sql.append(" ORDER BY pos LIMIT 1 OFFSET ?")
        args.add(position)
        val api = apiOf(source)
        return dao.queryVods(SimpleSQLiteQuery(sql.toString(), args.toTypedArray())).firstOrNull()?.let { vodOf(it, kind, api) }
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
        lockFilter(sql, args, kind ?: LIVE)
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
            dao.deleteTmdb(sourceId)
        }
    }

    // ----- TMDB matches. They are keyed by the provider's stream id, so they survive catalogue refreshes.

    class PendingTitle(val streamId: String, val name: String, val year: String)

    /** Titles never looked up yet, newest first. Only Xtream sources have stable stream ids. */
    suspend fun tmdbPending(source: Source, kind: VodKind, limit: Int): List<PendingTitle> {
        val sql = "SELECT v.* FROM vods v LEFT JOIN tmdb t ON t.sourceId = v.sourceId AND t.kind = v.kind" +
            " AND t.streamId = v.streamId WHERE v.sourceId = ? AND v.kind = ? AND v.streamId != ''" +
            " AND t.streamId IS NULL ORDER BY v.addedAt DESC, v.pos LIMIT ?"
        return dao.queryVods(SimpleSQLiteQuery(sql, arrayOf<Any>(source.id, kindName(kind), limit)))
            .map { PendingTitle(it.streamId, it.name, it.year) }
    }

    suspend fun saveTmdb(source: Source, kind: VodKind, streamId: String, match: Tmdb.Match?, now: Long): TmdbRow {
        val row = TmdbRow(
            sourceId = source.id,
            kind = kindName(kind),
            streamId = streamId,
            tmdbId = match?.id ?: 0,
            title = match?.title.orEmpty(),
            year = match?.year.orEmpty(),
            genres = match?.genres?.joinToString(",", prefix = ",", postfix = ",").orEmpty(),
            popularity = match?.popularity ?: 0.0,
            rating = match?.rating ?: 0.0,
            votes = match?.votes ?: 0,
            poster = match?.poster.orEmpty(),
            backdrop = match?.backdrop.orEmpty(),
            overview = match?.overview.orEmpty(),
            checkedAt = now,
        )
        dao.insertTmdb(listOf(row))
        return row
    }

    /** Matched and looked-up counts, for the settings screen. */
    suspend fun tmdbCounts(source: Source): Pair<Int, Int> =
        dao.countTmdbMatched(source.id) to dao.countTmdbChecked(source.id)

    /**
     * A row of matched titles: one entry per work (the provider's duplicate versions are merged),
     * most popular first or best rated first, optionally of one TMDB genre.
     */
    suspend fun smartRow(source: Source, kind: VodKind, genreId: Int?, byRating: Boolean, limit: Int): List<Vod> {
        val sql = StringBuilder(SMART_BEST).append(" WHERE t.sourceId = ? AND t.kind = ? AND t.tmdbId > 0")
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        if (genreId != null) {
            sql.append(" AND t.genres LIKE ?")
            args.add("%,$genreId,%")
        }
        if (byRating) sql.append(" AND t.votes >= 300")
        lockFilter(sql, args, kindName(kind))
        sql.append(" GROUP BY t.tmdbId ORDER BY ").append(if (byRating) "t.rating DESC" else "t.popularity DESC")
            .append(" LIMIT ?")
        args.add(limit)
        return enrichedList(source, kind, dao.queryTmdb(SimpleSQLiteQuery(sql.toString(), args.toTypedArray())))
    }

    /**
     * A page of the catalogue organised by ArkaTV: matched works only, versions merged, optionally of
     * one TMDB genre, in an order that comes from TMDB (popularity, rating, year, title) or from the
     * date the source added the title.
     */
    suspend fun smartPage(
        source: Source,
        kind: VodKind,
        genreId: Int?,
        sort: VodSort,
        descending: Boolean?,
        limit: Int,
        offset: Int,
        filter: VodFilter = VodFilter.NONE,
    ): List<Vod> {
        // Sorting by date added already uses an aggregate, so the version shown is then unspecified.
        val sql = StringBuilder(if (sort == VodSort.RECENT) SMART_FROM else SMART_BEST)
            .append(" WHERE t.sourceId = ? AND t.kind = ? AND t.tmdbId > 0")
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        if (genreId != null) {
            sql.append(" AND t.genres LIKE ?")
            args.add("%,$genreId,%")
        }
        versionFilter(sql, args, filter, "v.name")
        if (filter.minRating > 0) {
            sql.append(" AND t.rating >= ?")
            args.add(filter.minRating)
        }
        if (filter.yearFrom > 0 || filter.yearTo > 0) {
            sql.append(" AND CAST(t.year AS INTEGER) BETWEEN ? AND ?")
            args.add(if (filter.yearFrom > 0) filter.yearFrom else 1)
            args.add(if (filter.yearTo > 0) filter.yearTo else 9999)
        }
        val excluded = filter.excludeIds.take(VodFilter.MAX_EXCLUDED)
        if (excluded.isNotEmpty()) {
            sql.append(" AND v.streamId NOT IN (").append(excluded.joinToString(",") { "?" }).append(")")
            args.addAll(excluded)
        }
        // A rating only means something with enough votes behind it.
        if (sort == VodSort.RATING) sql.append(" AND t.votes >= 50")
        lockFilter(sql, args, kindName(kind))
        val direction = if (descending ?: (sort != VodSort.NAME)) "DESC" else "ASC"
        val order = when (sort) {
            VodSort.NAME -> "t.title COLLATE NOCASE $direction"
            VodSort.RECENT -> "MAX(v.addedAt) $direction"
            VodSort.YEAR -> "t.year $direction, t.popularity DESC"
            VodSort.RATING -> "t.rating $direction, t.votes DESC"
            VodSort.POPULAR, VodSort.PROVIDER -> "t.popularity $direction"
        }
        sql.append(" GROUP BY t.tmdbId ORDER BY ").append(order).append(" LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)
        return enrichedList(source, kind, dao.queryTmdb(SimpleSQLiteQuery(sql.toString(), args.toTypedArray())))
    }

    /** Titles TMDB does not know, or not looked up yet: shown as the source provides them, newest first. */
    suspend fun unmatchedPage(source: Source, kind: VodKind, limit: Int, offset: Int): List<Vod> {
        val sql = StringBuilder(UNMATCHED_FROM).append(UNMATCHED_WHERE)
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        lockFilter(sql, args, kindName(kind))
        sql.append(" ORDER BY v.addedAt DESC, v.pos LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)
        val api = apiOf(source)
        return dao.queryVods(SimpleSQLiteQuery(sql.toString().replace("COUNT(*)", "v.*"), args.toTypedArray()))
            .map { vodOf(it, kind, api) }
    }

    suspend fun smartCounts(source: Source, kind: VodKind, genres: List<Int>): SmartCounts {
        suspend fun count(genreId: Int?): Int {
            val sql = StringBuilder(
                "SELECT COUNT(DISTINCT t.tmdbId) FROM tmdb t JOIN vods v ON v.sourceId = t.sourceId AND v.kind = t.kind" +
                    " AND v.streamId = t.streamId WHERE t.sourceId = ? AND t.kind = ? AND t.tmdbId > 0"
            )
            val args = ArrayList<Any>()
            args.add(source.id)
            args.add(kindName(kind))
            if (genreId != null) {
                sql.append(" AND t.genres LIKE ?")
                args.add("%,$genreId,%")
            }
            lockFilter(sql, args, kindName(kind))
            return dao.queryInt(SimpleSQLiteQuery(sql.toString(), args.toTypedArray()))
        }
        val unmatchedSql = StringBuilder(UNMATCHED_FROM).append(UNMATCHED_WHERE)
        val unmatchedArgs = ArrayList<Any>()
        unmatchedArgs.add(source.id)
        unmatchedArgs.add(kindName(kind))
        lockFilter(unmatchedSql, unmatchedArgs, kindName(kind))
        val unmatched = dao.queryInt(SimpleSQLiteQuery(unmatchedSql.toString(), unmatchedArgs.toTypedArray()))
        return SmartCounts(count(null), unmatched, genres.associateWith { count(it) })
    }

    private suspend fun enrichedList(source: Source, kind: VodKind, matches: List<TmdbRow>): List<Vod> {
        if (matches.isEmpty()) return emptyList()
        val ids = matches.map { it.streamId }
        val rows = dao.queryVods(
            SimpleSQLiteQuery(
                "SELECT * FROM vods WHERE sourceId = ? AND kind = ? AND streamId IN (" + ids.joinToString(",") { "?" } + ")",
                (listOf<Any>(source.id, kindName(kind)) + ids).toTypedArray(),
            )
        ).associateBy { it.streamId }
        val api = apiOf(source)
        return matches.mapNotNull { match ->
            rows[match.streamId]?.let { enriched(vodOf(it, kind, api), match) }
        }
    }

    private fun enriched(vod: Vod, match: TmdbRow): Vod = vod.copy(
        name = match.title.ifBlank { vod.name },
        poster = match.poster.ifBlank { vod.poster },
        backdrop = match.backdrop,
        plot = match.overview.ifBlank { vod.plot },
        rating = if (match.rating > 0) Tmdb.ratingText(match.rating) else vod.rating,
        year = match.year.ifBlank { vod.year },
    )

    /** TMDB id of a matched title, or 0. */
    suspend fun tmdbId(source: Source, kind: VodKind, streamId: String): Int =
        dao.tmdbIdOf(source.id, kindName(kind), streamId) ?: 0

    /** The other versions of the same work offered by the source (4K, HD, another language…). */
    suspend fun variants(source: Source, kind: VodKind, streamId: String): List<Vod> {
        val tmdbId = dao.tmdbIdOf(source.id, kindName(kind), streamId) ?: return emptyList()
        if (tmdbId <= 0) return emptyList()
        val sql = StringBuilder(
            "SELECT v.* FROM vods v JOIN tmdb t ON t.sourceId = v.sourceId AND t.kind = v.kind AND t.streamId = v.streamId" +
                " WHERE v.sourceId = ? AND v.kind = ? AND t.tmdbId = ?"
        )
        val args = ArrayList<Any>()
        args.add(source.id)
        args.add(kindName(kind))
        args.add(tmdbId)
        lockFilter(sql, args, kindName(kind))
        sql.append(" ORDER BY v.pos LIMIT 12")
        val api = apiOf(source)
        return dao.queryVods(SimpleSQLiteQuery(sql.toString(), args.toTypedArray())).map { vodOf(it, kind, api) }
    }

    companion object {
        const val LIVE = "live"
        const val MOVIE = "movie"
        const val SERIES = "series"
        const val BATCH = 500
        const val MAX_CATEGORY_FILTER = 50
        const val MAX_LOCKED = 400
        private const val SMART_JOIN = " FROM tmdb t JOIN vods v ON v.sourceId = t.sourceId AND v.kind = t.kind" +
            " AND v.streamId = t.streamId"
        const val SMART_FROM = "SELECT t.*$SMART_JOIN"

        // When a work exists in several versions, the one shown is the French one, then the best
        // picture. SQLite returns the other columns from the row that holds the maximum.
        private const val VERSION_SCORE = "(CASE WHEN v.name LIKE 'FR %' OR v.name LIKE 'FR-%' OR v.name LIKE '%VF%'" +
            " OR v.name LIKE '%FRENCH%' THEN 300 WHEN v.name LIKE '%MULTI%' THEN 200" +
            " WHEN v.name LIKE '%VOSTFR%' THEN 100 ELSE 150 END" +
            " + CASE WHEN v.name LIKE '%4K%' OR v.name LIKE '%UHD%' THEN 40" +
            " WHEN v.name LIKE '%FHD%' OR v.name LIKE '%1080%' THEN 30" +
            " WHEN v.name LIKE 'SD %' OR v.name LIKE '% SD' OR v.name LIKE '% SD %' THEN 10 ELSE 20 END)"
        const val SMART_BEST = "SELECT t.*, MAX($VERSION_SCORE) AS best$SMART_JOIN"
        const val UNMATCHED_FROM = "SELECT COUNT(*) FROM vods v LEFT JOIN tmdb t ON t.sourceId = v.sourceId" +
            " AND t.kind = v.kind AND t.streamId = v.streamId"
        const val UNMATCHED_WHERE = " WHERE v.sourceId = ? AND v.kind = ? AND (t.streamId IS NULL OR t.tmdbId = 0)"
    }
}
