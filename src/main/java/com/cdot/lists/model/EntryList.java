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

import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.view.EntryListItemView;
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
import java.util.Stack;

/**
 * Base class for lists of items
 */
public abstract class EntryList extends EntryListItem {
    private static final String TAG = EntryList.class.getSimpleName();
    // Is this list being displayed sorted?
    public boolean sort = false;
    // Undo stack
    Stack<ArrayList<Remove>> mRemoves;
    // The basic list
    private final ArrayList<EntryListItem> mData = new ArrayList<>();

    /**
     * @param parent the list that contains this list (or null for the root)
     */
    EntryList(EntryList parent) {
        super(parent);
        mRemoves = new Stack<>();
    }

    /**
     * Copy constructor
     *
     * @param parent the list that contains this list (or null for the root)
     * @param copy   the item being copied
     */
    EntryList(EntryList parent, EntryList copy) {
        super(parent, copy);
        mRemoves = new Stack<>();
        sort = copy.sort;
    }

    @Override // implements EntryListItem
    public void fromJSON(JSONObject job) throws JSONException {
        clear();
        try {
            sort = job.getBoolean("sort");
        } catch (JSONException je) {
            sort = false;
        }
    }

    @Override // implements EntryListItem
    public JSONObject toJSON() {
        JSONObject job = new JSONObject();
        JSONArray its = new JSONArray();
        for (EntryListItem cl : mData)
            its.put(cl.toJSON());
        try {
            job.put("items", its);
            job.put("sort", sort);
        } catch (JSONException je) {
            Log.e(TAG, "" + je);
        }
        return job;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        w.writeNext(new String[]{"Item", "Checked"});
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
     * Factory for creating item views
     *
     * @param item item to view
     * @param cxt  context of the view
     * @return a new view
     */
    public abstract EntryListItemView makeItemView(EntryListItem item, EntryListFragment cxt);

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     */
    public void add(EntryListItem item) {
        mData.add(item);
    }

    /**
     * Get the entry at the given index
     *
     * @param i index of the list to remove
     * @return the index of the added item
     */
    EntryListItem get(int i) {
        if (i >= mData.size())
            return null;
        return mData.get(i);
    }

    /**
     * Put a new item at a specified position in the list
     *
     * @param item the item to add
     * @param i    the index of the added item
     */
    public void put(int i, EntryListItem item) {
        mData.add(i, item);
    }

    /**
     * Empty the list
     */
    public void clear() {
        mData.clear();
        mRemoves.clear();
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
        mData.remove(item);
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
        for (Remove it : items)
            mData.add(it.index, it.item);
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
     * Load the object from JSON or CSV read from the stream
     *
     * @param stream source of the JSON or CSV
     * @throws Exception if something goes wrong
     */
    public void fromStream(InputStream stream) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String data;
        StringBuilder sb = new StringBuilder();
        while ((data = br.readLine()) != null)
            sb.append(data).append("\n");
        data = sb.toString();
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
