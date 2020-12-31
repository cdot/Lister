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
import com.opencsv.exceptions.CsvException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.*

/**
 * Base class for lists of items
 */
abstract class EntryList : EntryListItem {
    // The basic list
    private var mData = ArrayList<EntryListItem>()

    // Undo stack
    private var mRemoves = Stack<ArrayList<Remove>>()

    /**
     * Construct new list
     */
    internal constructor()

    /**
     * Copy constructor
     *
     * @param copy the item being copied
     */
    internal constructor(copy: EntryList) : super(copy) {
        mRemoves = Stack()
    }

    @Throws(JSONException::class)
    override fun fromJSON(jo: JSONObject) {
        clear()
        super.fromJSON(jo)
    }

    /**
     * Get a set of the legal flag names for this entry list
     *
     * @return a set of flag names
     */
    override val flagNames: Set<String>
        get() = super.flagNames.plus(displaySorted).plus(warnAboutDuplicates)

    /**
     * Get the default value for this flag
     *
     * @param key flag name
     * @return flag value
     */
    override fun getFlagDefault(key: String): Boolean {
        return if (warnAboutDuplicates == key) true else super.getFlagDefault(key)
    }

    // implements EntryListItem
    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        val its = JSONArray()
        for (cl in mData) its.put(cl.toJSON())
        try {
                job.put("items", its)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return job
    }

    // implement EntryListItem
    override fun toCSV(w: CSVWriter) {
        for (it in mData) {
            it.toCSV(w)
        }
    }

    // implement EntryListItem
    override fun toPlainString(tab: String): String {
        val sb = StringBuilder()
        sb.append(tab).append(text).append(":\n")
        for (next in mData) {
            sb.append(next.toPlainString(tab + "\t")).append("\n")
        }
        return sb.toString()
    }

    // implement EntryListItem
    override fun equals(other: Any?): Boolean {
        if (other is EntryList) {
            if (!super.equals(other) || other.size() != size()) return false
            for (oit in other.mData) {
                val i = oit.text?.let { findByText(it, true) }
                if (i == null || oit != i) return false
            }
            return true
        } else
            return super.equals(other)
    }

    val data: List<EntryListItem>
        get() = mData

    /**
     * Make a copy of the data list. This is so the created list can be sorted before display without
     * impacting the underlying list.
     *
     * @return a copy of the list of entry items.
     */
    fun cloneItemList(): List<EntryListItem> {
        return mData.clone() as List<EntryListItem>
    }

    /**
     * Get the current list size
     *
     * @return size
     */
    fun size(): Int {
        return mData.size
    }

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     */
    fun addChild(item: EntryListItem) {
        if (item.parent != null) (item.parent as EntryList).remove(item, false)
        mData.add(item)
        item.parent = this
        notifyChangeListeners()
    }

    /**
     * Put a new item at a specified position in the list
     *
     * @param item the item to add
     * @param i    the index of the added item
     * @throws IndexOutOfBoundsException if the index is out of range (index < 0 || index > size())
     */
    fun put(i: Int, item: EntryListItem) {
        if (item.parent != null) (item.parent as EntryList).remove(item, false)
        mData.add(i, item)
        item.parent = this
        notifyChangeListeners()
    }

    /**
     * Empty the list
     */
    fun clear() {
        while (mData.size > 0)
            remove(mData[0], false)
        mRemoves.clear()
        notifyChangeListeners()
    }

    val removeCount: Int
        get() = mRemoves.size

    /**
     * Remove the given item from the list
     *
     * @param item item to remove
     */
    fun remove(item: EntryListItem, undo: Boolean) {
        Log.d(TAG, "remove")
        if (undo) {
            if (mRemoves.size == 0) mRemoves.push(ArrayList())
            mRemoves.peek().add(Remove(mData.indexOf(item), item))
        }
        item.parent = null
        mData.remove(item)
        notifyChangeListeners()
    }

    /**
     * Say if items in this list should be interactively moveable
     */
    open val itemsAreMoveable: Boolean
            get() = false

    /**
     * Call to start a new undo set. An undo will undo all the delete operations in the most
     * recent undo set.
     */
    fun newUndoSet() {
        mRemoves.push(ArrayList())
    }

    /**
     * Undo the last remove. All operations with the same undo tag will be undone.
     *
     * @return the number of items restored
     */
    fun undoRemove(): Int {
        if (mRemoves.size == 0) return 0
        val items = mRemoves.pop()
        if (items.size == 0) return 0
        for (it in items) {
            mData.add(it.index, it.item)
            it.item.parent = this
        }
        notifyChangeListeners()
        return items.size
    }

    /**
     * Find an item in the list by text string
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return matched item or null if not found
     */
    fun findByText(str: String, matchCase: Boolean): EntryListItem? {
        for (item in mData) {
            if (item.text.equals(str, ignoreCase = true)) return item
        }
        if (matchCase) return null
        for (item in mData) {
            val t = item.text
            if (t != null && t.toLowerCase().contains(str.toLowerCase())) return item
        }
        return null
    }

    /**
     * Find an item in the list by session UID
     *
     * @param uid item to find
     * @return matched item or null if not found
     */
    fun findBySessionUID(uid: Int): EntryListItem? {
        for (item in mData) {
            if (item.sessionUID == uid) return item
        }
        return null
    }

    /**
     * Get the index of the item in the list, or -1 if it's not there
     * c.f. findByText(), sortedIndexOf
     *
     * @param ci item to get the index of
     * @return the index of the item in the list, or -1 if it's not there
     */
    fun indexOf(ci: EntryListItem?): Int {
        return mData.indexOf(ci)
    }

    /**
     * Load the object from the given mime type read from the stream
     *
     * @param stream   source of the JSON or CSV
     * @param mimeType data type to expect (or null if unknown)
     * @throws Exception if something goes wrong
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun fromStream(stream: InputStream?, mimeType: String = "application/json") {
        val br = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line : String?
        while (br.readLine().also { line = it } != null) sb.append(line!!).append("\n")
        val data = sb.toString()
        when (mimeType) {
            "application/json" -> {
                try {
                    // See if it's JSON
                    fromJSON(JSONObject(data))
                } catch (je: JSONException) {
                    Log.d(TAG, "" + je)
                    throw Exception("Format error, could not read JSON")
                }
            }
            "text/csv" -> {
                try {
                    fromCSV(CSVReader(StringReader(data)))
                } catch (csve: CsvException) {
                    Log.d(TAG, "" + csve)
                    throw Exception("Format error, could not read CSV")
                }
            }
            else -> {
                try {
                    // See if it's JSON
                    fromJSON(JSONObject(data))
                } catch (je: JSONException) {
                    // See if it's CSV...
                    try {
                        fromCSV(CSVReader(StringReader(data)))
                    } catch (csve: CsvException) {
                        throw Exception("Format error, could not read JSON or CSV")
                    }
                }
            }
        }
    }

    // An item that has been removed, and the index it was removed from, for undos
    private class Remove internal constructor(var index: Int, var item: EntryListItem)

    companion object {
        const val displaySorted = "sort"
        const val warnAboutDuplicates = "warndup"
        private val TAG = EntryList::class.java.simpleName
    }
}