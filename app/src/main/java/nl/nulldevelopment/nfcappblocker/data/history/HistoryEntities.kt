package nl.nulldevelopment.nfcappblocker.data.history

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One period of blocking (D-044). No foreign key to the profile: the history stays when a block
 * list is deleted. [endedBy] holds a [SessionEnd] name; null while the session is running.
 */
@Entity(tableName = "block_sessions", indices = [Index("started_at"), Index("ended_at")])
data class BlockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at") val endedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "ended_by") val endedBy: String? = null,
)

/** The block screen covering an app once (D-044). */
@Entity(tableName = "block_screen_views", indices = [Index("shown_at")])
data class BlockScreenViewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "shown_at") val shownAtEpochMillis: Long,
)
