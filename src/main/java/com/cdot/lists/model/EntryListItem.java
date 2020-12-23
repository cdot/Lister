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

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface to items in an EntryList
 */
public abstract class EntryListItem {
    private static final String TAG = EntryListItem.class.getSimpleName();

    // The list that contains this list.
    private final EntryList mParent;
    private final List<ChangeListener> mListeners = new ArrayList<>();
    // Label, or list name
    private String mText;

    EntryListItem(EntryList parent) {
        mParent = parent;
        mText = null;
    }

    /**
     * Copy constructor. Only the item text is copied.
     *
     * @param copy the item to copy from
     */
    EntryListItem(EntryList parent, EntryListItem copy) {
        this(parent);
        setText(copy.getText());
    }

    /**
     * Add a change listener. Change listeners will be notified whenever a change is made
     *
     * @param l the listener
     */
    public void addChangeListener(ChangeListener l) {
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
        for (ChangeListener cl : mListeners)
            cl.onListChanged(this);
    }

    /**
     * Get the list that contains this item
     *
     * @return the containing list, or null for the root
     */
    public EntryList getContainer() {
        return mParent;
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
     * Load from a JSON object
     *
     * @param jo JSON object
     * @throws JSONException if anything goes wrong
     */
    abstract void fromJSON(JSONObject jo) throws JSONException;

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
            Log.e(TAG, "" + je);
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
    abstract JSONObject toJSON();

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
}
