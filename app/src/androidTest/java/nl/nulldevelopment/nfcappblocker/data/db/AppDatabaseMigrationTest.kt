package nl.nulldevelopment.nfcappblocker.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.room3.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
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
}
