package com.example.status_saver

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

enum class StatusType { IMAGE, VIDEO }

data class StatusItem(
    val uri: Uri,
    val name: String,
    val type: StatusType,
    val saved: Boolean = false,        // lives in your StatusSaver gallery folder
    val downloaded: Boolean = false,   // already saved from a live status
    val lastModified: Long = 0L,
) : Parcelable {

    private constructor(parcel: Parcel) : this(
        uri = Uri.parse(parcel.readString().orEmpty()),
        name = parcel.readString().orEmpty(),
        type = StatusType.valueOf(parcel.readString() ?: StatusType.IMAGE.name),
        saved = parcel.readInt() == 1,
        downloaded = parcel.readInt() == 1,
        lastModified = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uri.toString())
        parcel.writeString(name)
        parcel.writeString(type.name)
        parcel.writeInt(if (saved) 1 else 0)
        parcel.writeInt(if (downloaded) 1 else 0)
        parcel.writeLong(lastModified)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<StatusItem> {
        override fun createFromParcel(parcel: Parcel): StatusItem = StatusItem(parcel)
        override fun newArray(size: Int): Array<StatusItem?> = arrayOfNulls(size)
    }
}