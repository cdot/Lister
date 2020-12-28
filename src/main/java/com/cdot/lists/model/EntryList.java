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
import com.opencsv.exceptions.CsvException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Base class for lists of items
 */
public abstract class EntryList extends EntryListItem {
    public static final String displaySorted = "sort";
    public static final String warnAboutDuplicates = "warndup";
    private static final String TAG = EntryList.class.getSimpleName();
    // The basic list
    private final ArrayList<EntryListItem> mData = new ArrayList<>();

    // Undo stack
    Stack<ArrayList<Remove>> mRemoves = new Stack<>();

    /**
     * Construct new list
     */
    EntryList() {
    }

    /**
     * Copy constructor
     *
     * @param copy the item being copied
     */
    EntryList(EntryList copy) {
        super(copy);
        mRemoves = new Stack<>();
    }

    @Override // EntryListItem
    public void fromJSON(JSONObject job) throws JSONException {
        clear();
        super.fromJSON(job);
    }

    /**
     * Get a set of the legal flag names for this entry list
     *
     * @return a set of flag names
     */
    @Override // EntryListItem
    public Set<String> getFlagNames() {
        Set<String> s = super.getFlagNames();
        s.add(displaySorted);
        s.add(warnAboutDuplicates);
        return s;
    }

    /**
     * Get the default value for this flag
     *
     * @param key flag name
     * @return flag value
     */
    @Override // EntryListItem
    public boolean getFlagDefault(String key) {
        if (warnAboutDuplicates.equals(key))
            return true;
        return super.getFlagDefault(key);
    }

    @Override // implements EntryListItem
    public JSONObject toJSON() {
        JSONObject job = super.toJSON();
        JSONArray its = new JSONArray();
        for (EntryListItem cl : mData)
            its.put(cl.toJSON());
        try {
            job.put("items", its);
        } catch (JSONException je) {
            Log.e(TAG, "" + je);
        }
        return job;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        for (EntryListItem it : mData) {
            it.toCSV(w);
        }
    }

    @Override // implement EntryListItem
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append(getText()).append(":\n");
        for (EntryListItem next : mData) {
            sb.append(next.toPlainString(tab + "\t")).append("\n");
        }
        return sb.toString();
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem other) {
        EntryList oth = (EntryList) other;
        if (!super.equals(oth) || oth.size() != size())
            return false;

        for (EntryListItem oit : oth.mData) {
            EntryListItem i = findByText(oit.getText(), true);
            if (i == null || !oit.equals(i))
                return false;
        }
        return true;
    }

    public List<EntryListItem> getData() {
        return mData;
    }

    /**
     * Make a copy of the data list. This is so the created list can be sorted before display without
     * impacting the underlying list.
     *
     * @return a copy of the list of entry items.
     */
    public List<EntryListItem> cloneItemList() {
        return (List<EntryListItem>) mData.clone();
    }

    /**
     * Get the current list size
     *
     * @return size
     */
    public int size() {
        return mData.size();
    }

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     */
    public void addChild(EntryListItem item) {
        if (item.getParent() != null)
            ((EntryList) item.getParent()).remove(item, false);
        mData.add(item);
        item.setParent(this);
        notifyChangeListeners();
    }

    /**
     * Put a new item at a specified position in the list
     *
     * @param item the item to add
     * @param i    the index of the added item
     * @throws IndexOutOfBoundsException if the index is out of range (index < 0 || index > size())
     */
    public void put(int i, EntryListItem item) {
        if (item.getParent() != null)
            ((EntryList) item.getParent()).remove(item, false);
        mData.add(i, item);
        item.setParent(this);
        notifyChangeListeners();
    }

    /**
     * Empty the list
     */
    public void clear() {
        mData.clear();
        mRemoves.clear();
        notifyChangeListeners();
    }

    public int getRemoveCount() {
        return mRemoves.size();
    }

    /**
     * Remove the given item from the list
     *
     * @param item item to remove
     */
    public void remove(EntryListItem item, boolean undo) {
        Log.d(TAG, "remove");
        if (undo) {
            if (mRemoves.size() == 0)
                mRemoves.push(new ArrayList<>());
            mRemoves.peek().add(new Remove(mData.indexOf(item), item));
        }
        item.setParent(null);
        mData.remove(item);
        notifyChangeListeners();
    }

    public boolean itemsAreMoveable() {
        return !getFlag(Checklist.moveCheckedItemsToEnd);
    }

    /**
     * Call to start a new undo set. An undo will undo all the delete operations in the most
     * recent undo set.
     */
    public void newUndoSet() {
        mRemoves.push(new ArrayList<>());
    }

    /**
     * Undo the last remove. All operations with the same undo tag will be undone.
     *
     * @return the number of items restored
     */
    public int undoRemove() {
        if (mRemoves.size() == 0)
            return 0;
        ArrayList<Remove> items = mRemoves.pop();
        if (items.size() == 0)
            return 0;
        for (Remove it : items) {
            mData.add(it.index, it.item);
            it.item.setParent(this);
        }
        notifyChangeListeners();
        return items.size();
    }

    /**
     * Find an item in the list by text string
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return matched item or null if not found
     */
    public EntryListItem findByText(String str, boolean matchCase) {
        for (EntryListItem item : mData) {
            if (item.getText().equalsIgnoreCase(str))
                return item;
        }
        if (matchCase)
            return null;
        for (EntryListItem item : mData) {
            if (item.getText().toLowerCase().contains(str.toLowerCase()))
                return item;
        }
        return null;
    }

    /**
     * Find an item in the list by session UID
     *
     * @param uid item to find
     * @return matched item or null if not found
     */
    public EntryListItem findBySessionUID(int uid) {
        for (EntryListItem item : mData) {
            if (item.getSessionUID() == uid)
                return item;
        }
        return null;
    }

    /**
     * Get the index of the item in the list, or -1 if it's not there
     * c.f. findByText(), sortedIndexOf
     *
     * @param ci item to get the index of
     * @return the index of the item in the list, or -1 if it's not there
     */
    public int indexOf(EntryListItem ci) {
        return mData.indexOf(ci);
    }

    /**
     * Load the object from the given mime type read from the stream
     *
     * @param stream   source of the JSON or CSV
     * @param mimeType data type to expect (or null if unknown)
     * @throws Exception if something goes wrong
     */
    public void fromStream(InputStream stream, String mimeType) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String data;
        StringBuilder sb = new StringBuilder();
        while ((data = br.readLine()) != null)
            sb.append(data).append("\n");
        data = sb.toString();
        if ("application/json".equals(mimeType)) {
            try {
                // See if it's JSON
                fromJSON(new JSONObject(data));
            } catch (JSONException je) {
                Log.d(TAG, "" + je);
                throw new Exception("Format error, could not read JSON");
            }
        } else if ("text/csv".equals(mimeType)) {
            try {
                fromCSV(new CSVReader(new StringReader(data)));
            } catch (CsvException csve) {
                Log.d(TAG, "" + csve);
                throw new Exception("Format error, could not read CSV");
            }
        } else {
            try {
                // See if it's JSON
                fromJSON(new JSONObject(data));
            } catch (JSONException je) {
                // See if it's CSV...
                try {
                    fromCSV(new CSVReader(new StringReader(data)));
                } catch (CsvException csve) {
                    throw new Exception("Format error, could not read JSON or CSV");
                }
            }
        }
    }

    /**
     * Load the object from JSON or CSV read from the stream
     *
     * @param stream source of the JSON or CSV
     * @throws Exception if something goes wrong
     */
    public void fromStream(InputStream stream) throws Exception {
        fromStream(stream, "application/json");
    }

    // An item that has been removed, and the index it was removed from, for undos
    private static class Remove {
        int index;
        EntryListItem item;

        Remove(int ix, EntryListItem it) {
            index = ix;
            item = it;
        }
    }
}
