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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A checklist of checkable items. An item in a Checklists, so it has behaviours from
 * EntryListItem, inherited via EntryList
 */
class Checklist(name: String) : EntryList(name) {

    constructor() : this(NO_NAME)

    override val flagNames: Set<String>
        get() = super.flagNames.plus(CHECKED_AT_END).plus(DELETE_CHECKED)

    override val childrenAreMoveable: Boolean
        get() = !getFlag(CHECKED_AT_END)

    override fun copy(other: EntryListItem) : EntryListItem {
        super.copy(other)
        other as EntryList
        for (item in other.children) addChild(ChecklistItem().copy(item))
        return this
    }

    @Throws(JSONException::class)
    override fun fromJSON(jo: JSONObject) : EntryListItem {
        text = jo.getString("name")
        val items = jo.getJSONArray("items")
        for (i in 0 until items.length())
            addChild(ChecklistItem().fromJSON(items.getJSONObject(i)))
        return super.fromJSON(jo)
    }

    // CSV checklists consist of a set of rows, each with the list name in the first column,
    // the item name in the second column, and the done status in the third column
    @Throws(Exception::class)  // EntryListItem
    override fun fromCSV(r: CSVReader): EntryListItem {
        if (text == r.peek()[0]) {
            // recognised header row
            while (r.peek() != null && r.peek()[0] == text)
                addChild(ChecklistItem().fromCSV(r))
        }
        return this
    }

    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        try {
            job.put("name", text)
            val items = JSONArray()
            for (item in children) {
                items.put(item.toJSON())
            }
            job.put("items", items)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return job
    }

    companion object {
        private val TAG = Checklist::class.simpleName
        const val CHECKED_AT_END = "movend"
        const val DELETE_CHECKED = "autodel"
    }
}