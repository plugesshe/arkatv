package tv.arka.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Catalogue cache. Rows keep the provider order through [pos].
 * [streamId] is the Xtream identifier (URLs are rebuilt from the source credentials, never stored);
 * [secret] is the encrypted stream URL for M3U entries, which may embed credentials.
 */
@Entity(tableName = "channels", primaryKeys = ["sourceId", "pos"])
data class ChannelRow(
    val sourceId: String,
    val pos: Int,
    val streamId: String,
    val secret: String,
    val name: String,
    val logo: String,
    val categoryId: String,
    val epgId: String,
    val number: Int,
)

@Entity(tableName = "vods", primaryKeys = ["sourceId", "kind", "pos"])
data class VodRow(
    val sourceId: String,
    val kind: String,
    val pos: Int,
    val streamId: String,
    val secret: String,
    val ext: String,
    val name: String,
    val poster: String,
    val categoryId: String,
    val rating: String,
    val addedAt: Long,
    val plot: String,
    val year: String,
)

@Entity(tableName = "categories", primaryKeys = ["sourceId", "kind", "pos"])
data class CategoryRow(
    val sourceId: String,
    val kind: String,
    val pos: Int,
    val categoryId: String,
    val name: String,
)

@Entity(tableName = "programs", primaryKeys = ["sourceId", "channel", "startAt"])
data class ProgramRow(
    val sourceId: String,
    val channel: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY pos")
    suspend fun channels(sourceId: String): List<ChannelRow>

    @Query("SELECT * FROM vods WHERE sourceId = :sourceId AND kind = :kind ORDER BY pos")
    suspend fun vods(sourceId: String, kind: String): List<VodRow>

    @Query("SELECT * FROM categories WHERE sourceId = :sourceId ORDER BY kind, pos")
    suspend fun categories(sourceId: String): List<CategoryRow>

    @Query("SELECT * FROM programs WHERE sourceId = :sourceId AND endAt > :from ORDER BY channel, startAt")
    suspend fun programs(sourceId: String, from: Long): List<ProgramRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(rows: List<ChannelRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVods(rows: List<VodRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(rows: List<CategoryRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(rows: List<ProgramRow>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteChannels(sourceId: String)

    @Query("DELETE FROM vods WHERE sourceId = :sourceId")
    suspend fun deleteVods(sourceId: String)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteCategories(sourceId: String)

    @Query("DELETE FROM programs WHERE sourceId = :sourceId")
    suspend fun deletePrograms(sourceId: String)
}

@Database(
    entities = [ChannelRow::class, VodRow::class, CategoryRow::class, ProgramRow::class],
    version = 1,
    exportSchema = false,
)
abstract class ArkaDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao

    companion object {
        /**
         * This database only caches what the provider can send again, so a schema change may
         * rebuild it. User data (sources, favourites, list, progress) lives in [tv.arka.app.data.Store].
         */
        fun create(context: Context): ArkaDatabase =
            Room.databaseBuilder(context, ArkaDatabase::class.java, "arkatv-catalog.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
