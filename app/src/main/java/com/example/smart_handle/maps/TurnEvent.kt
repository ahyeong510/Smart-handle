package com.example.smart_handle.maps

import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng

// 🔹 좌/우/직진 종류
enum class TurnType {
    LEFT,
    RIGHT,
    STRAIGHT
}

data class TurnEvent(
    val location: LatLng,     // 턴 위치 (위도/경도)
    val type: TurnType,       // 턴 종류
    var trigger50: Boolean = false,  // 50m 알림 했는지
    var trigger25: Boolean = false   // 25m 알림 했는지
) : Parcelable {

    // Parcel 에서 읽어오는 생성자
    constructor(parcel: Parcel) : this(
        location = LatLng(
            parcel.readDouble(),   // latitude
            parcel.readDouble()    // longitude
        ),
        type = TurnType.valueOf(
            parcel.readString() ?: TurnType.STRAIGHT.name
        ),
        trigger50 = parcel.readByte() != 0.toByte(),
        trigger25 = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        // 위치
        parcel.writeDouble(location.latitude)
        parcel.writeDouble(location.longitude)
        // enum 은 name 문자열로 저장
        parcel.writeString(type.name)
        // 플래그들
        parcel.writeByte(if (trigger50) 1 else 0)
        parcel.writeByte(if (trigger25) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TurnEvent> {
        override fun createFromParcel(parcel: Parcel): TurnEvent = TurnEvent(parcel)
        override fun newArray(size: Int): Array<TurnEvent?> = arrayOfNulls(size)
    }
}
