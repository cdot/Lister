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
abstract class EntryList internal constructor(t: String) : EntryListItem(t) {

    val children = ArrayList<EntryListItem>()

    // Undo stack
    private var undoStack = Stack<ArrayList<Remove>>()

    /**
     * Get a set of the legal flag names for this entry list
     *
     * @return a set of flag names
     */
    override val flagNames: Set<String>
        get() = super.flagNames.plus(DISPLAY_SORTED).plus(WARN_ABOUT_DUPLICATES)

    override fun getFlagDefault(key: String): Boolean {
        return if (WARN_ABOUT_DUPLICATES == key) true else super.getFlagDefault(key)
    }

    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        val its = JSONArray()
        for (cl in children) its.put(cl.toJSON())
        try {
            job.put("items", its)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return job
    }

    override fun toCSV(w: CSVWriter) {
        for (it in children) {
            it.toCSV(w)
        }
    }

    override fun toPlainString(tab: String): String {
        val sb = StringBuilder()
        sb.append(tab).append(text).append(":\n")
        for (next in children)
            sb.append(next.toPlainString(tab + "\t")).append("\n")
        return sb.toString()
    }

    override fun sameAs(other: EntryListItem?): Boolean {
        if (other is EntryList) {
            if (!super.sameAs(other) || other.size() != size()) return false
            for (oit in other.children) {
                val found = findByText(oit.text, true)
                if (found == null || !oit.sameAs(found)) return false
            }
            return true
        } else
            return super.sameAs(other)
    }

    /**
     * Make a copy of the child list. This is so the created list can be manipulated
     * without impacting the underlying list.
     *
     * @return a copy of the list of child items.
     */
    fun cloneChildren(): List<EntryListItem> {
        return children.clone() as List<EntryListItem>
    }

    /**
     * Get the current list size
     *
     * @return size
     */
    fun size(): Int {
        return children.size
    }

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     */
    fun addChild(item: EntryListItem) {
        if (item.parent != null) (item.parent as EntryList).remove(item, false)
        children.add(item)
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
    fun insertAt(i: Int, item: EntryListItem) {
        if (item.parent != null) (item.parent as EntryList).remove(item, false)
        children.add(i, item)
        item.parent = this
        notifyChangeListeners()
    }

    /**
     * Empty the list
     */
    fun clear() {
        while (children.size > 0)
            remove(children[0], false)
        undoStack.clear()
        notifyChangeListeners()
    }

    val removeCount: Int
        get() = undoStack.size

    /**
     * Remove the given item from the list
     *
     * @param item item to remove
     */
    fun remove(item: EntryListItem, undo: Boolean) {
        Log.d(TAG, "remove")
        if (undo) {
            if (undoStack.size == 0) undoStack.push(ArrayList())
            undoStack.peek().add(Remove(children.indexOf(item), item))
        }
        item.parent = null
        children.remove(item)
        notifyChangeListeners()
    }

    /**
     * Say if items in this list should be interactively moveable. By default they are not.
     */
    open val childrenAreMoveable: Boolean
    // Surely this has to be true, or we can't sort Checklists
        get() = false

    /**
     * Call to start a new undo set. An undo will undo all the delete operations in the most
     * recent undo set.
     */
    fun newUndoSet() {
        undoStack.push(ArrayList())
    }

    /**
     * Undo the last remove. All operations with the same undo tag will be undone.
     *
     * @return the number of items restored
     */
    fun undoRemove(): Int {
        if (undoStack.size == 0) return 0
        val items = undoStack.pop()
        if (items.size == 0) return 0
        for (it in items) {
            children.add(it.index, it.item)
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
        for (item in children) {
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
        for (item in children) {
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
        for (it in children) {
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
        for (item in children) {
            if (item.text.equals(str, ignoreCase = true)) return item
        }
        if (matchCase) return null
        for (item in children)
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
        for (item in children) {
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
        return children.indexOf(ci)
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