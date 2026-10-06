package nl.nulldevelopment.nfcappblocker.data.tags

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileEntity

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            // Deleting a profile unlinks its tags; they stay paired.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("profile_id")],
)
data class TagEntity(
    /** Canonical UUID string, as written on the tag. */
    @PrimaryKey val uuid: String,
    val name: String,
    /** Colon-separated hex, e.g. "04:1D:42:74:D4:2A:81". Null when unknown. */
    @ColumnInfo(name = "hardware_uid") val hardwareUid: String?,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    /** The profile this tag controls (D-003); null while unlinked. */
    @ColumnInfo(name = "profile_id") val profileId: Long? = null,
)
