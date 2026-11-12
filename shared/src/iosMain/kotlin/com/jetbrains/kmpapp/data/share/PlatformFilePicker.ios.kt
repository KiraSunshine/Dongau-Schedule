package com.jetbrains.kmpapp.data.share

actual object PlatformFilePicker {
    actual fun pickTextFile(onPicked: (fileName: String?, content: String?) -> Unit) {
        onPicked(null, null)
    }
}
