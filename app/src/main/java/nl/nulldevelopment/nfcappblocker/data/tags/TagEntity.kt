package nl.nulldevelopment.nfcappblocker.data.tags

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    /** Canonical UUID string, as written on the tag. */
    @PrimaryKey val uuid: String,
    val name: String,
    /** Colon-separated hex, e.g. "04:1D:42:74:D4:2A:81". Null when unknown. */
    @ColumnInfo(name = "hardware_uid") val hardwareUid: String?,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
)
