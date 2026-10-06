package nl.nulldevelopment.nfcappblocker.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import nl.nulldevelopment.nfcappblocker.data.tags.TagDao
import nl.nulldevelopment.nfcappblocker.data.tags.TagEntity

@Database(entities = [TagEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "nfc-app-blocker.db"
    }
}
