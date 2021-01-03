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
class ChecklistItem(t:String) : EntryListItem(t) {

    constructor() : this(NO_NAME)

    override val flagNames: Set<String>
        get() = super.flagNames.plus(IS_DONE)

    override fun sameAs(other: EntryListItem?): Boolean {
        if (other is ChecklistItem)
            if (getFlag(IS_DONE) != other.getFlag(IS_DONE))
                return false
        return super.sameAs(other)
    }

    @Throws(JSONException::class)
    override fun fromJSON(jo: JSONObject) : EntryListItem {
        text = jo.getString("name")
        return super.fromJSON(jo)
    }

    @Throws(Exception::class)
    override fun fromCSV(r: CSVReader): EntryListItem {
        val row = r.readNext()!!
        text = row[1]
        // "f", "false", "0", and "" are read as false. Any other value is read as true
        if (row[2].isEmpty() || row[2].matches(Regex("[Ff]([Aa][Ll][Ss][Ee])?|0"))) clearFlag(IS_DONE) else setFlag(IS_DONE)
        return this
    }

    override fun toJSON(): JSONObject {
        val iob = super.toJSON()
        try {
            iob.put("name", text)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return iob
    }

    override fun toCSV(w: CSVWriter) {
        val a = arrayOfNulls<String>(3)
        if (parent == null) a[0] = "" else a[0] = parent!!.text
        a[1] = text
        a[2] = if (getFlag(IS_DONE)) "T" else "F"
        w.writeNext(a)
    }

    override fun toPlainString(tab: String): String {
        val sb = StringBuilder()
        sb.append(tab).append(text)
        if (getFlag(IS_DONE)) sb.append(" *")
        return sb.toString()
    }

    companion object {
        const val IS_DONE = "done"
        private val TAG = ChecklistItem::class.simpleName
    }
}