package com.roohu.roohux.features.downloads.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.roohu.roohux.core.database.DownloadEntity

object DownloadActions {
    private fun fileExists(context: Context, d: DownloadEntity): Boolean {
        val uri = d.fileUri?.toUri() ?: return false
        return try { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false } catch (e: Exception) { false }
    }

    fun open(context: Context, d: DownloadEntity) {
        if (!fileExists(context, d)) { toast(context, "The file was moved or deleted."); return }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(d.fileUri!!.toUri(), d.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { toast(context, "No app can open this file type.") }
    }

    fun share(context: Context, d: DownloadEntity) {
        if (!fileExists(context, d)) { toast(context, "The file was moved or deleted."); return }
        val send = Intent(Intent.ACTION_SEND)
            .setType(d.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, d.fileUri!!.toUri())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share ${d.fileName}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the system Downloads app (files are stored in Downloads/RoohuX). */
    fun locate(context: Context) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(context, "Open your Files app and look in Downloads/RoohuX.")
        }
    }

    private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
