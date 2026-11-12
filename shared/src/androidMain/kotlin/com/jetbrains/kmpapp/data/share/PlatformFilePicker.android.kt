package com.jetbrains.kmpapp.data.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.jetbrains.kmpapp.data.storage.AndroidContextProvider
import java.io.BufferedReader
import java.io.InputStreamReader

actual object PlatformFilePicker {
    private var pendingCallback: ((String?, String?) -> Unit)? = null
    private var activityProvider: (() -> Activity?)? = null

    fun registerActivityProvider(provider: () -> Activity?) {
        activityProvider = provider
    }

    actual fun pickTextFile(onPicked: (fileName: String?, content: String?) -> Unit) {
        pendingCallback = onPicked
        val context = AndroidContextProvider.context ?: run {
            onPicked(null, null)
            pendingCallback = null
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/*", "application/json", "*/*"))
        }
        try {
            val activity = activityProvider?.invoke()
            if (activity != null) {
                activity.startActivityForResult(intent, REQUEST_CODE_PICK_TEXT_FILE)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                pendingCallback = null
            }
        } catch (_: Throwable) {
            pendingCallback = null
            onPicked(null, null)
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_PICK_TEXT_FILE) return
        val cb = pendingCallback
        pendingCallback = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            cb?.invoke(null, null)
            return
        }
        val uri = data.data!!
        val context = AndroidContextProvider.context
        if (context == null) {
            cb?.invoke(null, null)
            return
        }
        try {
            val name = queryDisplayName(context, uri)
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            cb?.invoke(name, content)
        } catch (_: Throwable) {
            cb?.invoke(null, null)
        }
    }

    private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    const val REQUEST_CODE_PICK_TEXT_FILE = 4107
}
