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
package com.cdot.lists.model;

import android.util.Log;

import androidx.annotation.CallSuper;

import com.cdot.lists.Lister;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface to items in an EntryList
 */
public abstract class EntryListItem {
    private static final String TAG = EntryListItem.class.getSimpleName();
    // UID's are assigned when an item is created. They allow us to track list entries
    // across activities (list item text need not be unique). UIDs are not serialised.
    private static int sUID = 1;
    // The list that contains this list.
    protected EntryList mParent;
    private final List<ChangeListener> mListeners = new ArrayList<>();
    protected int mUID;
    // Label, or list name
    private String mText;
    // Boolean flags
    private Map<String, Boolean> mFlags = new HashMap<>();

    EntryListItem() {
        mText = null;
        mUID = sUID++;
    }

    /**
     * Copy constructor. Only the item text is copied.
     *
     * @param copy the item to copy from
     */
    EntryListItem(EntryListItem copy) {
        this();
        mFlags.putAll(copy.mFlags);
        setText(copy.getText());
    }

    /**
     * Get the session UID for this item
     */
    public int getSessionUID() {
        return mUID;
    }

    /**
     * Add a change listener. Change listeners will be notified whenever a change is made
     *
     * @param l the listener
     */
    public void addChangeListener(ChangeListener l) {
        if (!mListeners.contains(l))
            mListeners.add(l);
    }

    /**
     * Remove a change listener.
     *
     * @param l the listener
     */
    public void removeChangeListener(ChangeListener l) {
        mListeners.remove(l);
    }

    /**
     * Notify any views of this list that the list contents have changed and redisplay is required.
     */
    public void notifyChangeListeners() {
        if (getParent() != null)
            getParent().notifyChangeListeners();
        for (ChangeListener cl : mListeners)
            cl.onListChanged(this);
    }

    /**
     * Get the list that contains this item
     *
     * @return the containing list, or null for the root
     */
    public EntryList getParent() {
        return mParent;
    }

    /**
     * Set the list that contains this item. The item is neither removed from the previous parent,
     * nor added to the new parent.
     */
    void setParent(EntryList parent) {
        mParent = parent;
    }

    /**
     * Return false if the item is not moveable in the current list view
     */
    public abstract boolean isMoveable();

    /**
     * Get the item's text
     *
     * @return a text string representing the item in the list
     */
    public String getText() {
        return mText;
    }

    /**
     * Set the item's text
     *
     * @param str new text
     */
    public void setText(String str) {
        mText = str;
    }

    /**
     * Get a set of the legal flag names for this entry list. Subclasses override to add their
     * supported flags. By default, no flags are supported.
     *
     * @return a set of flag names
     */
    @CallSuper
    public Set<String> getFlagNames() {
        return new HashSet<>();
    }

    /**
     * Get the default value for a flag. Flag defaults are always false unless overridden.
     *
     * @param key flag name
     * @return flag value
     */
    public boolean getFlagDefault(String key) {
        return false;
    }

    /**
     * Get the current value for this flag
     *
     * @param key flag name
     * @return flag value
     */
    public boolean getFlag(String key) {
        if (!mFlags.containsKey(key))
            return getFlagDefault(key);
        Boolean v = mFlags.get(key);
        return (v == null) ? false : v;
    }

    /**
     * Set this flag true
     * @param key flag name
     */
    public void setFlag(String key) {
        mFlags.put(key, true);
    }

    /**
     * Set this flag false
     * @param key flag name
     */
    public void clearFlag(String key) {
        mFlags.put(key, false);
    }

    /**
     * Load from a JSON object. The base method imports all the flag settings.
     *
     * @param jo JSON object
     * @throws JSONException not thrown by this base method, but may be thrown by subclasses
     */
    @CallSuper
    void fromJSON(JSONObject jo) throws JSONException {
        for (String k : getFlagNames()) {
            try {
                if (jo.getBoolean(k))
                    setFlag(k);
                else
                    clearFlag(k);
            } catch (JSONException ignore) {
                if (getFlagDefault(k))
                    setFlag(k);
                else
                    clearFlag(k);
            }
        }
    }

    /**
     * Load from a JSON string
     *
     * @param js JSON string
     */
    public void fromJSON(String js) {
        try {
            JSONObject job = new JSONObject(js);
            fromJSON(job);
        } catch (JSONException je) {
            Log.e(TAG, Lister.stringifyException(je));
        }
    }

    /**
     * Load from a Comma-Separated Value object
     *
     * @param r a reader
     */
    abstract boolean fromCSV(CSVReader r) throws Exception;

    /**
     * Get the JSON object that represents the content of this object
     *
     * @return a JSONObject
     */
    @CallSuper
    JSONObject toJSON() {
        JSONObject job = new JSONObject();
        for (String k : getFlagNames()) {
            try {
                if (getFlag(k) != getFlagDefault(k))
                    job.put(k, getFlag(k));
            } catch (JSONException je) {
                Log.e(TAG, Lister.stringifyException(je));
            }
        }
        return job;
    }

    /**
     * Write the CSV that represents the content of this object
     *
     * @param w a CSV writer
     */
    abstract void toCSV(CSVWriter w);

    /**
     * Format the item for inclusion in a text representation
     */
    abstract String toPlainString(String tab);

    /**
     * Deep equality test. This should be is a comparison of text only; UIDs should be ignored
     *
     * @param other the other item
     */
    boolean equals(EntryListItem other) {
        return other != null && mText.equals(other.getText());
    }

    /**
     * To be implemented by listeners for changes
     */
    public interface ChangeListener {
        void onListChanged(EntryListItem item);
    }

    public String toString() {
        return getClass().getSimpleName() + ":" + getSessionUID() + "(" + getText() + ")";
    }
}
