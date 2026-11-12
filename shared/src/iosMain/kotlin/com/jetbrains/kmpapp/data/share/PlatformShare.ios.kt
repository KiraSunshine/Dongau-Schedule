package com.jetbrains.kmpapp.data.share

import platform.UIKit.UIApplication
import platform.Foundation.NSURL

actual object PlatformShare {
    actual fun openUrl(url: String) {
        try {
            val nsUrl = NSURL.URLWithString(url) ?: return
            UIApplication.sharedApplication.openURL(nsUrl)
        } catch (_: Throwable) {
        }
    }

    actual fun shareText(subject: String, text: String) {
        println("Share: $subject\n$text")
    }

    actual fun shareTextFile(fileName: String, text: String) {
        println("Share file: $fileName\n$text")
    }

    actual fun saveTextFile(fileName: String, text: String) {
        println("Save file: $fileName\n$text")
    }
}
