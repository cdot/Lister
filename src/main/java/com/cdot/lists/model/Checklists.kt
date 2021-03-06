/*
 * Copyright © 2020 C-Dot Consultants
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.cdot.lists.model

import android.util.Log
import com.cdot.lists.Lister
import com.opencsv.CSVReader
import org.json.JSONException
import org.json.JSONObject

/**
 * A list of Checklist.
 */
class Checklists : EntryList(NO_NAME) {

    // URI it was loaded from (or is a cache for). Kept here so it gets serialised with the cache,
    // and thereby detect when the cache doesn't correspond to the loaded URI
    var forUri : String? = null

    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        try {
            job.put("timestamp", timestamp)
            job.put("uri", forUri)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return job
    }

    @Throws(JSONException::class)  // EntryList
    override fun fromJSON(jo: JSONObject) : EntryListItem {
        try {
            timestamp = if (jo.has("timestamp")) jo.getLong("timestamp") else 0 // 0=unknown
            forUri = if (jo.has("uri")) jo.getString("uri") else ""
            val lists = jo.getJSONArray("items")
            for (i in 0 until lists.length())
                addChild(Checklist().fromJSON(lists.getJSONObject(i)))
            Log.d(TAG, "Extracted " + lists.length() + " lists from JSON")
        } catch (je: JSONException) {
            // Only one list
            Log.d(TAG, "Could not get lists from JSON, assume one list only")
            addChild(Checklist().fromJSON(jo))
        }
        return super.fromJSON(jo)
    }

    @Throws(Exception::class)  // EntryListItem
    override fun fromCSV(r: CSVReader): EntryListItem {
        var peke = r.peek()
        while (peke != null) {
            if (peke.isNotEmpty() && peke[0] != NO_NAME && peke[0] != "")
                addChild(Checklist(peke[0]).fromCSV(r))
            peke = r.peek()
        }
        return this
    }

    override fun toPlainString(tab: String): String {
        val sb = StringBuilder()
        for (item in children)
            sb.append(item.toPlainString(tab)).append("\n")
        return sb.toString()
    }

    override fun isMoreRecentVersionOf(other: EntryListItem): Boolean {
        other as Checklists
        return other.forUri == forUri && super.isMoreRecentVersionOf(other)
    }

    companion object {
        private val TAG = Checklists::class.simpleName
    }
}