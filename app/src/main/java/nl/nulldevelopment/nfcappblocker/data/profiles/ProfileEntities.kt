package nl.nulldevelopment.nfcappblocker.data.profiles

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
)

/** One app in a profile, by package name. Labels and icons are looked up live. */
@Entity(
    tableName = "profile_apps",
    primaryKeys = ["profile_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProfileAppEntity(
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
)

data class ProfileWithApps(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["profile_id"])
    val apps: List<ProfileAppEntity>,
)
