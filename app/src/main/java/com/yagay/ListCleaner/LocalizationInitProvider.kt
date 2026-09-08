package com.yagay.ListCleaner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/** Initializes the locale resource bridge before Application.onCreate. */
class LocalizationInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext
        if (app != null) AppLanguage.init(app)
        if (app is ListCleanerApp) {
            app.syncStatus.value = LocaleText.pick("等待连接", "Waiting for connection")
            app.runtime.value = RuntimeStatus()
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
