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
class Checklist : EntryList {
    constructor()
    constructor(name: String?) {
        text = name
    }

    /**
     * Process the JSON given and load from it
     *
     * @param job    the JSON object
     * @throws JSONException if something goes wrong
     */
    constructor(job: JSONObject) {
        fromJSON(job)
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy   list to clone
     */
    constructor(copy: Checklist) : super(copy) {
        for (item in copy.data) addChild(ChecklistItem(item as ChecklistItem))
    }

    // override EntryListItem
    override val flagNames: Set<String>
        get() = super.flagNames.plus(CHECKED_AT_END).plus(DELETE_CHECKED)

    // implement EntryListItem
    override val isMoveable: Boolean
        get() = true

    override val itemsAreMoveable: Boolean
        get() = !getFlag(CHECKED_AT_END)

    @Throws(JSONException::class)
    override fun fromJSON(jo: JSONObject) {
        clear()
        super.fromJSON(jo)
        text = jo.getString("name")
        val items = jo.getJSONArray("items")
        for (i in 0 until items.length()) {
            val ci = ChecklistItem(null as String?)
            ci.fromJSON(items.getJSONObject(i))
            addChild(ci)
        }
    }

    // CSV lists continue of a set of rows, each with the list name in the first column,
    // the item name in the second column, and the done status in the third column
    @Throws(Exception::class)  // EntryListItem
    override fun fromCSV(r: CSVReader): Boolean {
        if (r.peek() == null) return false
        if (text == null || text.equals(r.peek()[0])) {
            // recognised header row
            if (text == null) text = r.peek()[0]
            while (r.peek() != null && r.peek()[0] == text) {
                val ci = ChecklistItem()
                if (!ci.fromCSV(r)) break
                addChild(ci)
            }
        }
        return true
    }

    // EntryListItem
    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        try {
            job.put("name", text)
            val items = JSONArray()
            for (item in data) {
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