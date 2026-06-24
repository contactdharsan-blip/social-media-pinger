package com.quietping.data.db

import androidx.room.TypeConverter
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType

/**
 * Room [TypeConverter]s for the domain enums used by the entities. Enums are
 * persisted by their stable [Enum.name] (not ordinal) so reordering enum
 * constants never corrupts existing rows.
 */
class Converters {

    @TypeConverter
    fun appPackageToString(value: AppPackage): String = value.name

    @TypeConverter
    fun stringToAppPackage(value: String): AppPackage = AppPackage.valueOf(value)

    @TypeConverter
    fun triggerTypeToString(value: TriggerType): String = value.name

    @TypeConverter
    fun stringToTriggerType(value: String): TriggerType = TriggerType.valueOf(value)

    @TypeConverter
    fun messageStatusToString(value: MessageStatus): String = value.name

    @TypeConverter
    fun stringToMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)

    @TypeConverter
    fun captureSourceToString(value: CaptureSource): String = value.name

    @TypeConverter
    fun stringToCaptureSource(value: String): CaptureSource = CaptureSource.valueOf(value)

    @TypeConverter
    fun soundPresetToString(value: SoundPreset): String = value.name

    @TypeConverter
    fun stringToSoundPreset(value: String): SoundPreset = SoundPreset.valueOf(value)
}
