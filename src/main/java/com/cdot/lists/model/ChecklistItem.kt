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
import com.opencsv.CSVWriter
import org.json.JSONException
import org.json.JSONObject

/**
 * An item in a Checklist
 */
class ChecklistItem : EntryListItem {
    constructor()
    constructor(str: String?) {
        text = str
    }

    /**
     * Construct by copying the given item into the given checklist
     *
     * @param copy      item to copy
     */
    constructor(copy: ChecklistItem) : this(copy.text) {
        for (f in flagNames) {
            if (copy.getFlag(f)) setFlag(f) else clearFlag(f)
        }
    }

    // EntryListItem
    override val flagNames: Set<String>
        get() = super.flagNames.plus(isDone)

    // implement EntryListItem
    override val isMoveable: Boolean
        get() = parent == null || parent!!.itemsAreMoveable

    // implement EntryListItem
    override fun equals(other: Any?): Boolean {
        if (other is ChecklistItem)
            if (getFlag(isDone) != (other as ChecklistItem).getFlag(isDone))
                return false;
        return super.equals(other);
    }

    @Throws(JSONException::class)  // implement EntryListItem
    override fun fromJSON(jo: JSONObject) {
        super.fromJSON(jo)
        text = jo.getString("name")
    }

    @Throws(Exception::class)  // implement EntryListItem
    override fun fromCSV(r: CSVReader): Boolean {
        val row = r.readNext() ?: return false
        text = row[1]
        // "f", "false", "0", and "" are read as false. Any other value is read as true
        if (row[2].isEmpty() || row[2].matches(Regex("[Ff]([Aa][Ll][Ss][Ee])?|0"))) clearFlag(isDone) else setFlag(isDone)
        return true
    }

    // implement EntryListItem
    override fun toJSON(): JSONObject {
        val iob = super.toJSON()
        try {
            iob.put("name", text)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return iob
    }

    // implement EntryListItem
    override fun toCSV(w: CSVWriter) {
        val a = arrayOfNulls<String>(3)
        if (parent == null) a[0] = "" else a[0] = parent!!.text
        a[1] = text
        a[2] = if (getFlag(isDone)) "T" else "F"
        w.writeNext(a)
    }

    // implement EntryListItem
    override fun toPlainString(tab: String): String {
        val sb = StringBuilder()
        sb.append(tab).append(text)
        if (getFlag(isDone)) sb.append(" *")
        return sb.toString()
    }

    companion object {
        const val isDone = "done"
        private val TAG = ChecklistItem::class.java.simpleName
    }
}