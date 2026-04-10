package com.example.smart_handle.maps

import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng

enum class TurnType {
    LEFT,
    RIGHT,
    STRAIGHT
}

data class TurnEvent(
    val location: LatLng,
    val type: TurnType,
    var isContinuous: Boolean = false,
    var trigger50: Boolean = false,
    var trigger25: Boolean = false
) : Parcelable {

    constructor(parcel: Parcel) : this(
        location = LatLng(
            parcel.readDouble(),
            parcel.readDouble()
        ),
        type = TurnType.valueOf(
            parcel.readString() ?: TurnType.STRAIGHT.name
        ),
        isContinuous = parcel.readByte() != 0.toByte(),
        trigger50 = parcel.readByte() != 0.toByte(),
        trigger25 = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(location.latitude)
        parcel.writeDouble(location.longitude)
        parcel.writeString(type.name)
        parcel.writeByte(if (isContinuous) 1 else 0)
        parcel.writeByte(if (trigger50) 1 else 0)
        parcel.writeByte(if (trigger25) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TurnEvent> {
        override fun createFromParcel(parcel: Parcel): TurnEvent = TurnEvent(parcel)
        override fun newArray(size: Int): Array<TurnEvent?> = arrayOfNulls(size)
    }
}