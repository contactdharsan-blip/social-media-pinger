package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietping.domain.model.AppPackage

/**
 * Room row for a starred contact. The pair ([appPackage], [handle]) is unique so a
 * contact is not starred twice for the same app.
 */
@Entity(
    tableName = "vip_contacts",
    indices = [
        Index(value = ["app_package", "handle"], unique = true)
    ]
)
data class VipContactEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "handle")
    val handle: String,

    @ColumnInfo(name = "app_package")
    val appPackage: AppPackage
)
