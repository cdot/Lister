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
 * Constructed by reading a JSON file.
 */
class Checklists : EntryList() {
    private var mTimestamp // time it was last changed
            : Long = 0

    // URI it was loaded from (or is a cache for). Kept here so it gets serialised with the cache,
    // and thereby detect when the cache doesn't correspond to the loaded URI
    var forUri : String? = null

    // implement EntryListItem
    override val isMoveable: Boolean
        get() = false

    // EntryList
    override fun notifyChangeListeners() {
        mTimestamp = System.currentTimeMillis()
        super.notifyChangeListeners()
    }

    // EntryList
    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        try {
            job.put("timestamp", mTimestamp)
            job.put("uri", forUri)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return job
    }

    @Throws(JSONException::class)  // EntryList
    override fun fromJSON(jo: JSONObject) {
        try {
            super.fromJSON(jo)
            mTimestamp = if (jo.has("timestamp")) jo.getLong("timestamp") else 0 // 0=unknown
            forUri = if (jo.has("uri")) jo.getString("uri") else ""
            val lists = jo.getJSONArray("items")
            for (i in 0 until lists.length()) addChild(Checklist(lists.getJSONObject(i)))
            Log.d(TAG, "Extracted " + lists.length() + " lists from JSON")
        } catch (je: JSONException) {
            // Only one list
            Log.d(TAG, "Could not get lists from JSON, assume one list only")
            addChild(Checklist(jo))
        }
    }

    @Throws(Exception::class)  // EntryListItem
    override fun fromCSV(r: CSVReader): Boolean {
        while (r.peek() != null) {
            val list = Checklist()
            list.fromCSV(r)
            addChild(list)
        }
        return true
    }

    // EntryList
    override fun toPlainString(tab: String): String {
        val sb = StringBuilder()
        for (item in data) {
            sb.append(item.toPlainString(tab)).append("\n")
        }
        return sb.toString()
    }

    /**
     * Determine if this is a more recent version of another set of checklists, as determined by the
     * timestamp that is set whenever anything in the lists changes.
     *
     * @return false if the two lists don't come from the same URI, or if the other list's
     * time stamp is more recent than this list.
     */
    fun isMoreRecentVersionOf(other: Checklists): Boolean {
        return other.forUri == forUri && mTimestamp > other.mTimestamp
    }

    companion object {
        private val TAG = Checklists::class.simpleName
    }
}