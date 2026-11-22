package app.lumora.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WallpaperEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LumoraDatabase : RoomDatabase() {

    abstract fun wallpaperDao(): WallpaperDao

    companion object {
        private const val NAME = "lumora.db"

        fun build(context: Context): LumoraDatabase =
            Room.databaseBuilder(context.applicationContext, LumoraDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
