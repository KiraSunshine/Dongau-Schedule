package com.jetbrains.kmpapp.data.share

expect object PlatformFilePicker {
    fun pickTextFile(onPicked: (fileName: String?, content: String?) -> Unit)
}
