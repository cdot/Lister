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
 * Base class for lists of items. An EntryList can itself be an item in an EntryList, so it inherits from
 * EntryListItem
 */
abstract class EntryList : EntryListItem {
    internal constructor(t: String) : super(t)

    // The basic list
    private var mData = ArrayList<EntryListItem>()

    // Undo stack
    private var mRemoves = Stack<ArrayList<Remove>>()

    /**
     * Copy constructor
     *
     * @param copy the item being copied
     */
    internal constructor(copy: EntryList) : super(copy) {
        mRemoves = Stack()
    }

    /**
     * Get a set of the legal flag names for this entry list
     *
     * @return a set of flag names
     */
    override val flagNames: Set<String>
        get() = super.flagNames.plus(DISPLAY_SORTED).plus(WARN_ABOUT_DUPLICATES)

    /**
     * Get the default value for this flag
     *
     * @param key flag name
     * @return flag value
     */
    override fun getFlagDefault(key: String): Boolean {
        return if (WARN_ABOUT_DUPLICATES == key) true else super.getFlagDefault(key)
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
    override fun sameAs(other: EntryListItem?): Boolean {
        if (other is EntryList) {
            if (!super.sameAs(other) || other.size() != size()) return false
            for (oit in other.mData) {
                val found = findByText(oit.text, true)
                if (found == null || !oit.sameAs(found)) return false
            }
            return true
        } else
            return super.sameAs(other)
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
        val i: Any = mData.clone()
        return i as List<EntryListItem>
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
     * Get the number of checked items
     *
     * @return the number of checked items
     */
    fun countFlaggedEntries(flag: String): Int {
        var i = 0
        for (item in data) {
            if (item.getFlag(flag)) i++
        }
        return i
    }

    /**
     * Make a global change to the "checked" status of all items in the list
     *
     * @param check true to set items as checked, false to set as unchecked
     */
    fun setFlagOnAll(flag: String, check: Boolean): Boolean {
        var changed = false
        for (item in data) {
            val ci = item as ChecklistItem
            if (ci.getFlag(ChecklistItem.IS_DONE) != check) {
                if (check) ci.setFlag(flag) else ci.clearFlag(ChecklistItem.IS_DONE)
                changed = true
            }
        }
        return changed
    }

    /**
     * Delete all the items in the list that have the flag
     *
     * @return number of items deleted
     */
    fun deleteAllFlagged(flag: String): Int {
        val kill = ArrayList<EntryListItem>()
        for (it in data) {
            if (it.getFlag(flag)) kill.add(it)
        }
        newUndoSet()
        for (dead in kill) {
            remove(dead, true)
        }
        return kill.size
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
        for (item in mData)
            if (item.text.toLowerCase(Locale.getDefault()).contains(str.toLowerCase(Locale.getDefault()))) return item
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
     * Load the object from the stream. A first attempt will be made to parse JSON, and if that
     * fails it will try to load it as CSV
     *
     * @param stream   source of the JSON or CSV
     * @param mimeType data type to expect (or null if unknown)
     * @throws Exception if something goes wrong
     */
    @Throws(Exception::class)
    fun fromStream(stream: InputStream) {
        val br = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line: String?
        while (br.readLine().also { line = it } != null) sb.append(line!!).append("\n")
        val data = sb.toString()
        try {
            // Try and read
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

    // An item that has been removed, and the index it was removed from, for undos
    private class Remove internal constructor(var index: Int, var item: EntryListItem)

    companion object {
        const val DISPLAY_SORTED = "sort"
        const val WARN_ABOUT_DUPLICATES = "warndup"
        private val TAG = EntryList::class.simpleName
    }
}