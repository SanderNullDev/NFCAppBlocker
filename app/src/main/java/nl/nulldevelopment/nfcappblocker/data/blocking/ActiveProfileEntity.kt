package nl.nulldevelopment.nfcappblocker.data.blocking

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileEntity

/**
 * A profile that is blocking right now. A table rather than a single value, so several active
 * profiles later is a logic change, not a migration (D-004).
 */
@Entity(
    tableName = "active_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            // Deleting an active profile stops its blocking.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActiveProfileEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
)
