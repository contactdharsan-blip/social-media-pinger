package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietping.domain.model.AlertStyle
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.RuleAction
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType

/**
 * Room row for a user-defined alert rule. Indexed by [appPackage] for the
 * per-app rule list, and by [enabled] so the RuleEngine can cheaply load only
 * active rules.
 */
@Entity(
    tableName = "rules",
    indices = [
        Index(value = ["app_package"]),
        Index(value = ["enabled"])
    ]
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "app_package")
    val appPackage: AppPackage,

    @ColumnInfo(name = "type")
    val type: TriggerType,

    @ColumnInfo(name = "pattern")
    val pattern: String,

    @ColumnInfo(name = "sound_preset")
    val soundPreset: SoundPreset,

    @ColumnInfo(name = "dnd_override")
    val dndOverride: Boolean,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "alert_style", defaultValue = "STANDARD")
    val alertStyle: AlertStyle = AlertStyle.STANDARD,

    @ColumnInfo(name = "action", defaultValue = "ALERT")
    val action: RuleAction = RuleAction.ALERT,

    @ColumnInfo(name = "window_start_min", defaultValue = "-1")
    val windowStartMin: Int = -1,

    @ColumnInfo(name = "window_end_min", defaultValue = "-1")
    val windowEndMin: Int = -1
)
