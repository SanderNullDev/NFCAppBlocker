package nl.nulldevelopment.nfcappblocker.data.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveProfileDao
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveProfileEntity
import nl.nulldevelopment.nfcappblocker.data.history.BlockScreenViewEntity
import nl.nulldevelopment.nfcappblocker.data.history.BlockSessionEntity
import nl.nulldevelopment.nfcappblocker.data.history.HistoryDao
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileAppEntity
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileDao
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileEntity
import nl.nulldevelopment.nfcappblocker.data.tags.TagDao
import nl.nulldevelopment.nfcappblocker.data.tags.TagEntity

@Database(
    entities = [
        TagEntity::class, ProfileEntity::class, ProfileAppEntity::class, ActiveProfileEntity::class,
        BlockSessionEntity::class, BlockScreenViewEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2 (Phase 2): profiles, their apps, and a profile link on tags.
        AutoMigration(from = 1, to = 2),
        // v3 (Phase 3): which profile is blocking right now.
        AutoMigration(from = 2, to = 3),
        // v4 (Phase 8): usage history, blocking sessions and block screen views (D-044).
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun profileDao(): ProfileDao
    abstract fun activeProfileDao(): ActiveProfileDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "nfc-app-blocker.db"
    }
}
