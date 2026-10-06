package nl.nulldevelopment.nfcappblocker.data.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileAppEntity
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileDao
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileEntity
import nl.nulldevelopment.nfcappblocker.data.tags.TagDao
import nl.nulldevelopment.nfcappblocker.data.tags.TagEntity

@Database(
    entities = [TagEntity::class, ProfileEntity::class, ProfileAppEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2 (Phase 2): profiles, their apps, and a profile link on tags.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun profileDao(): ProfileDao

    companion object {
        const val NAME = "nfc-app-blocker.db"
    }
}
