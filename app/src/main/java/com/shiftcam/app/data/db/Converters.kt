package com.shiftcam.app.data.db

import androidx.room.TypeConverter
import com.shiftcam.domain.reading.ReadingUnit
import com.shiftcam.domain.session.AddressSource
import com.shiftcam.domain.session.PhotoType
import com.shiftcam.domain.session.RemarkOwnerType
import com.shiftcam.domain.session.SessionStatus
import com.shiftcam.domain.session.WorkType

/** 枚举一律按 name 落库，禁止写 value 序号，避免增删枚举导致历史数据错位。 */
class Converters {

    @TypeConverter
    fun toSessionStatus(value: String?): SessionStatus? = value?.let { SessionStatus.valueOf(it) }

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus?): String? = value?.name

    @TypeConverter
    fun toReadingUnit(value: String?): ReadingUnit? = value?.let { ReadingUnit.valueOf(it) }

    @TypeConverter
    fun fromReadingUnit(value: ReadingUnit?): String? = value?.name

    @TypeConverter
    fun toWorkType(value: String?): WorkType? = value?.let { WorkType.valueOf(it) }

    @TypeConverter
    fun fromWorkType(value: WorkType?): String? = value?.name

    @TypeConverter
    fun toRemarkOwnerType(value: String?): RemarkOwnerType? = value?.let { RemarkOwnerType.valueOf(it) }

    @TypeConverter
    fun fromRemarkOwnerType(value: RemarkOwnerType?): String? = value?.name

    @TypeConverter
    fun toPhotoType(value: String?): PhotoType? = value?.let { PhotoType.valueOf(it) }

    @TypeConverter
    fun fromPhotoType(value: PhotoType?): String? = value?.name

    @TypeConverter
    fun toAddressSource(value: String?): AddressSource? = value?.let { AddressSource.valueOf(it) }

    @TypeConverter
    fun fromAddressSource(value: AddressSource?): String? = value?.name
}
