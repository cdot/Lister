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
import androidx.annotation.CallSuper
import com.cdot.lists.Lister
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Interface to items in an EntryList. These are either ChecklistItem or Checklist
 */
abstract class EntryListItem internal constructor(t: String) {

    /**
     * Label, or list name, or NO_NAME for root or as-yet unspecified
     */
    var text: String = t

    // The list that contains this item - or null if not yet known, or root
    var parent: EntryList? = null

    // Listeners to changes made to this list
    private val listeners: MutableList<ChangeListener> = ArrayList()

    /**
     * The session UID for this item
     */
    var sessionUID: Int

    // Boolean flags
    private val flags: MutableMap<String, Boolean?> = HashMap()

    // time it was last changed
    protected var timestamp = System.currentTimeMillis()

    /**
     * Add a change listener. Change listeners will be notified whenever a change is made
     *
     * @param l the listener
     */
    fun addChangeListener(l: ChangeListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    /**
     * Remove a change listener.
     *
     * @param l the listener
     */
    fun removeChangeListener(l: ChangeListener?) {
        listeners.remove(l)
    }

    /**
     * Notify any views of this list that the list contents have changed and redisplay is required.
     */
    open fun notifyChangeListeners() {
        timestamp = System.currentTimeMillis()
        // It'll usually be the parent list that carries the change listeners
        if (parent != null) parent!!.notifyChangeListeners()
        for (cl in listeners) cl.onListChanged(this)
    }

    /**
     * Get a set of the legal flag names for this entry list. Subclasses override to add their
     * supported flags. By default, no flags are supported.
     *
     * @return a set of flag names
     */
    open val flagNames: Set<String>
        get() = HashSet()

    /**
     * Get the default value for a flag. Flag defaults are always false unless overridden.
     *
     * @param key flag name
     * @return flag value
     */
    open fun getFlagDefault(key: String): Boolean {
        return false
    }

    /**
     * Get the current value for this flag
     *
     * @param key flag name
     * @return flag value
     */
    fun getFlag(key: String): Boolean {
        if (!flags.containsKey(key)) return getFlagDefault(key)
        val v = flags[key]
        return v ?: false
    }

    /**
     * Set this flag true
     * @param key flag name
     */
    fun setFlag(key: String) {
        flags[key] = true
    }

    /**
     * Set this flag false
     * @param key flag name
     */
    fun clearFlag(key: String) {
        flags[key] = false
    }

    /**
     * Copy fields from another object into this
     */
    open fun copy(other: EntryListItem): EntryListItem {
        text = other.text
        flags.putAll(other.flags)
        return this
    }

    /**
     * Load from a JSON object. The base method imports all the flag settings.
     *
     * @param jo JSON object
     * @return this
     * @throws JSONException not thrown by this base method, but may be thrown by subclasses
     */
    @CallSuper
    @Throws(JSONException::class)
    open fun fromJSON(jo: JSONObject): EntryListItem {
        for (k in flagNames) {
            try {
                if (jo.getBoolean(k)) setFlag(k) else clearFlag(k)
            } catch (ignore: JSONException) {
                if (getFlagDefault(k)) setFlag(k) else clearFlag(k)
            }
        }
        return this
    }

    /**
     * Load from a JSON string
     *
     * @param js JSON string
     * @return this
     */
    fun fromJSON(js: String): EntryListItem {
        try {
            val job = JSONObject(js)
            fromJSON(job)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return this
    }

    /**
     * Load from a Comma-Separated Value object
     *
     * @param r a reader
     */
    @Throws(Exception::class)
    abstract fun fromCSV(r: CSVReader): EntryListItem

    /**
     * Get the JSON object that represents the content of this object
     *
     * @return a JSONObject
     */
    @CallSuper
    open fun toJSON(): JSONObject {
        val job = JSONObject()
        for (k in flagNames) {
            try {
                if (getFlag(k) != getFlagDefault(k)) job.put(k, getFlag(k))
            } catch (je: JSONException) {
                Log.e(TAG, Lister.stringifyException(je))
            }
        }
        return job
    }

    /**
     * Write the CSV that represents the content of this object
     *
     * @param w a CSV writer
     */
    abstract fun toCSV(w: CSVWriter)

    /**
     * Format the item for inclusion in a text representation
     */
    abstract fun toPlainString(tab: String): String

    /**
     * Deep equality test. This should be is a comparison of content only; UIDs should be ignored
     *
     * @param other the other item
     */
    open fun sameAs(other: EntryListItem?): Boolean {
        return text == other?.text
    }

    /**
     * Determine if this is a more recent version of another object of the sme type, as determined
     * by the timestamp that is set whenever anything changes.
     */
    open fun isMoreRecentVersionOf(other: EntryListItem): Boolean {
        return timestamp > other.timestamp
    }

    /**
     * To be implemented by listeners for changes
     */
    interface ChangeListener {
        fun onListChanged(item: EntryListItem)
    }

    override fun toString(): String {
        return "$TAG:$sessionUID($text)"
    }

    companion object {
        private val TAG = EntryListItem::class.simpleName

        // Rather than allowing names to be null, we choose to use a unique name for otherwise
        // namelists EntryListItems
        const val NO_NAME = "\bM\r Lis\te\r\b" // Very, very unlikely to encounter this!

        // UID's are assigned when an item is created. They allow us to track list entries
        // across activities (list item text need not be unique). UIDs are not serialised.
        private var sUID = 1
    }

    init {
        sessionUID = sUID++
    }
}