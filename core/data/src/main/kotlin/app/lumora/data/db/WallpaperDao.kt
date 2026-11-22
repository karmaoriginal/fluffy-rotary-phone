package app.lumora.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {

    @Query("SELECT * FROM wallpapers ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE kind = :kind ORDER BY addedAt DESC")
    fun observeByKind(kind: String): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE favorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<WallpaperEntity>>

    @Query(
        """
        SELECT * FROM wallpapers
        WHERE displayName LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
           OR author LIKE '%' || :query || '%'
        ORDER BY addedAt DESC
        """,
    )
    fun search(query: String): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): WallpaperEntity?

    @Query("SELECT * FROM wallpapers WHERE sourceId = :sourceId LIMIT 1")
    suspend fun findBySourceId(sourceId: String): WallpaperEntity?

    @Query("SELECT COUNT(*) FROM wallpapers WHERE kind = :kind")
    fun observeCount(kind: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM wallpapers")
    fun observeTotalBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WallpaperEntity): Long

    @Update
    suspend fun update(entity: WallpaperEntity)

    @Query("UPDATE wallpapers SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE wallpapers SET displayName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE wallpapers SET applyCount = applyCount + 1, lastAppliedAt = :timestamp WHERE id = :id")
    suspend fun markApplied(id: Long, timestamp: Long)

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM wallpapers")
    suspend fun clear()

    @Query("SELECT filePath FROM wallpapers")
    suspend fun allFilePaths(): List<String>
}
