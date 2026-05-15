package com.minimalist.launcher.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.minimalist.launcher.core.model.SearchResult

class ContactsRepository(private val context: Context) {

    fun search(query: String): List<SearchResult.Contact> {
        if (query.isBlank()) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val results = mutableListOf<SearchResult.Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )
        cursor?.use {
            val nameCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameCol) ?: continue
                val num  = it.getString(numCol)  ?: continue
                results.add(SearchResult.Contact(name, num))
            }
        }
        // One entry per contact name — keep first (highest-priority) number only.
        return results.distinctBy { it.name }
    }
}
