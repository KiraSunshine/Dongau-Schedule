package com.jetbrains.kmpapp.data.share

import android.content.Intent
import androidx.core.content.FileProvider
import com.jetbrains.kmpapp.data.storage.AndroidContextProvider
import java.io.File

actual object PlatformShare {
    actual fun openUrl(url: String) {
        val context = AndroidContextProvider.context ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    actual fun shareText(subject: String, text: String) {
        val context = AndroidContextProvider.context ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Throwable) {
        }
    }

    actual fun shareTextFile(fileName: String, text: String) {
        val context = AndroidContextProvider.context ?: return
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeName = fileName.ifBlank { "schedule.txt" }.let { if (it.endsWith(".txt", true)) it else "$it.txt" }
            val file = File(dir, safeName)
            file.writeText(text)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, safeName.removeSuffix(".txt"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Поделиться файлом").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
        }
    }

    actual fun saveTextFile(fileName: String, text: String) {
        val context = AndroidContextProvider.context ?: return
        try {
            val safeName = fileName.ifBlank { "schedule.txt" }.let { if (it.endsWith(".txt", true)) it else "$it.txt" }
            // Открываем системный диалог сохранения (SAF). Результат не обрабатываем здесь,
            // но пользователь может выбрать место и сохранить файл.
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, safeName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            // Также сохраняем копию в кэш/экспорт приложения для удобства (можно поделиться позже).
            val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            File(exportDir, safeName).writeText(text)
        } catch (_: Throwable) {
            // fallback: сохранить в экспорт приложения
            try {
                val safeName = fileName.ifBlank { "schedule.txt" }.let { if (it.endsWith(".txt", true)) it else "$it.txt" }
                val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
                File(exportDir, safeName).writeText(text)
            } catch (_: Throwable) {
            }
        }
    }
}
