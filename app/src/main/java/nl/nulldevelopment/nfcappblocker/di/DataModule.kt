package nl.nulldevelopment.nfcappblocker.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.nulldevelopment.nfcappblocker.apps.AndroidInstalledAppsSource
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.blocking.AndroidBlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.blocking.BlockingNotifications
import nl.nulldevelopment.nfcappblocker.blocking.BlockingNotifier
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveProfileDao
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.blocking.RoomBlockingRepository
import nl.nulldevelopment.nfcappblocker.data.db.AppDatabase
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileDao
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.profiles.RoomProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.RoomTagRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagDao
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.util.AndroidDefaultNameFormats
import nl.nulldevelopment.nfcappblocker.util.DefaultNameFormats
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun tagDao(database: AppDatabase): TagDao = database.tagDao()

    @Provides
    fun profileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun activeProfileDao(database: AppDatabase): ActiveProfileDao = database.activeProfileDao()

    @Provides
    fun clock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun tagRepository(impl: RoomTagRepository): TagRepository

    @Binds
    @Singleton
    abstract fun profileRepository(impl: RoomProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun blockingRepository(impl: RoomBlockingRepository): BlockingRepository

    @Binds
    abstract fun installedAppsSource(impl: AndroidInstalledAppsSource): InstalledAppsSource

    @Binds
    abstract fun defaultNameFormats(impl: AndroidDefaultNameFormats): DefaultNameFormats

    @Binds
    abstract fun blockerServiceStatus(impl: AndroidBlockerServiceStatus): BlockerServiceStatus

    @Binds
    abstract fun blockingNotifications(impl: BlockingNotifier): BlockingNotifications
}
