package com.jetbrains.kmpapp.data.share

expect object PlatformShare {
    fun shareText(subject: String, text: String)
    fun shareTextFile(fileName: String, text: String)
    fun saveTextFile(fileName: String, text: String)
    fun openUrl(url: String)
}
