package nl.nulldevelopment.nfcappblocker.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.room3.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * The test's own scratch file, never the app's real database. Tests now run without
     * uninstalling the app (CLAUDE.md), so a file from an earlier run must be removed first.
     */
    @Before
    fun deleteScratchDatabase() {
        instrumentation.targetContext.deleteDatabase(DATABASE)
    }

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(DATABASE),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun tagsPairedInVersion1SurviveVersion2WithoutAProfile() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO tags (uuid, name, hardware_uid, created_at) " +
                    "VALUES ('6e24ed8e-a569-4248-942c-fcbb1b99043c', 'Desk', '04:1D:42:74:D4:2A:81', 1000)",
            )
        }

        helper.runMigrationsAndValidate(2).use { connection ->
            connection.prepare("SELECT name, hardware_uid, created_at, profile_id FROM tags").use { row ->
                assertTrue(row.step())
                assertEquals("Desk", row.getText(0))
                assertEquals("04:1D:42:74:D4:2A:81", row.getText(1))
                assertEquals(1000L, row.getLong(2))
                assertTrue(row.isNull(3))
            }
            connection.prepare("SELECT COUNT(*) FROM profiles").use { row ->
                assertTrue(row.step())
                assertEquals(0L, row.getLong(0))
            }
        }
    }

    @Test
    fun profilesAppsAndLinkedTagsSurviveVersion3WithNothingActive() = runTest {
        helper.createDatabase(2).use { connection ->
            connection.execSQL("INSERT INTO profiles (id, name, created_at) VALUES (1, 'Focus', 1000)")
            connection.execSQL("INSERT INTO profile_apps (profile_id, package_name) VALUES (1, 'com.instagram.android')")
            connection.execSQL(
                "INSERT INTO tags (uuid, name, hardware_uid, created_at, profile_id) " +
                    "VALUES ('6e24ed8e-a569-4248-942c-fcbb1b99043c', 'Desk', NULL, 1000, 1)",
            )
        }

        helper.runMigrationsAndValidate(3).use { connection ->
            connection.prepare("SELECT p.name, a.package_name, t.name FROM profiles p " +
                "JOIN profile_apps a ON a.profile_id = p.id JOIN tags t ON t.profile_id = p.id").use { row ->
                assertTrue(row.step())
                assertEquals("Focus", row.getText(0))
                assertEquals("com.instagram.android", row.getText(1))
                assertEquals("Desk", row.getText(2))
            }
            connection.prepare("SELECT COUNT(*) FROM active_profiles").use { row ->
                assertTrue(row.step())
                assertEquals(0L, row.getLong(0))
            }
        }
    }

    @Test
    fun activeBlockingSurvivesVersion4WithAnEmptyHistory() = runTest {
        helper.createDatabase(3).use { connection ->
            connection.execSQL("INSERT INTO profiles (id, name, created_at) VALUES (1, 'Focus', 1000)")
            connection.execSQL("INSERT INTO profile_apps (profile_id, package_name) VALUES (1, 'com.instagram.android')")
            connection.execSQL("INSERT INTO active_profiles (profile_id, started_at) VALUES (1, 2000)")
        }

        helper.runMigrationsAndValidate(4).use { connection ->
            connection.prepare("SELECT profile_id, started_at FROM active_profiles").use { row ->
                assertTrue(row.step())
                assertEquals(1L, row.getLong(0))
                assertEquals(2000L, row.getLong(1))
            }
            connection.prepare("SELECT (SELECT COUNT(*) FROM block_sessions) + (SELECT COUNT(*) FROM block_screen_views)").use { row ->
                assertTrue(row.step())
                assertEquals(0L, row.getLong(0))
            }
        }
    }

    private companion object {
        const val DATABASE = "migration-test.db"
    }
}
