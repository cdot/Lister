/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Stack;

/**
 * Base class of things that can be serialised to a JSON representation and saved.
 */
abstract class EntryList implements EntryListItem {
    private final String TAG = "EntryList";

    protected long mUID;

    // The list that contains this list.
    private EntryList mParent;

    // The basic list
    protected ArrayList<EntryListItem> mUnsorted = new ArrayList<>();

    // Is this list being displayed sorted?
    protected boolean mShowSorted = false;
    // A sorted version of the list (sorts on getText())
    protected ArrayList<EntryListItem> mDisplayed = new ArrayList<>();

    // A temporary item used in dragging
    transient EntryListItem mMovingItem = null;

    boolean mInEditMode = false;

    /**
     * Adapter for the list. This is only created when the list is actually displayed.
     */
    static class Adapter extends ArrayAdapter<EntryListItem> {
        private final EntryList mList;

        Adapter(EntryList list, Context cxt) {
            super(cxt, 0);
            mList = list;
        }

        @Override // ArrayAdapter
        public @NonNull
        View getView(int i, View convertView, @NonNull ViewGroup viewGroup) {
            EntryListItem item = mList.getDisplayOrder().get(i);
            EntryListItemView itemView = (EntryListItemView) convertView;
            if (itemView == null)
                itemView = mList.makeItemView(item, getContext());
            else
                itemView.setItem(item);
            itemView.updateView();
            return itemView;
        }

        @Override
        public int getCount() {
            return mList.size();
        }
    }

    private EntryList.Adapter mArrayAdapter;

    /**
     * An item that has been removed, and the index it was removed from, for undos
     */
    private static class Remove {
        int index;
        EntryListItem item;

        Remove(int ix, EntryListItem it) {
            index = ix;
            item = it;
        }
    }

    // Undo stack
    Stack<ArrayList<Remove>> mRemoves;

    /**
     * Constructor
     *
     * @param parent the list that contains this list (or null for the root)
     */
    EntryList(EntryList parent) {
        mUID = Settings.getUID();
        mParent = parent;
        mRemoves = new Stack<>();
    }

    @Override // implement EntryListItem
    public long getUID() {
        return mUID;
    }

    @Override // implement EntryListItem
    public EntryList getContainer() {
        return mParent;
    }

    void setArrayAdapter(EntryList.Adapter adapter) {
        mArrayAdapter = adapter;
    }

    /**
     * Get an array giving the current ordering of the items in the displayed list.
     *
     * @return a sorted array of items
     */
    protected ArrayList<EntryListItem> getDisplayOrder() {
        return mDisplayed;
    }

    /**
     * Get the current list size
     *
     * @return size
     */
    int size() {
        return mUnsorted.size();
    }

    /**
     * Factory for creating item views
     *
     * @param item item to view
     * @param cxt  context of the view
     * @return a new view
     */
    abstract EntryListItemView makeItemView(EntryListItem item, Context cxt);

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     */
    void add(EntryListItem item) {
        mUnsorted.add(item);
        updateDisplayOrder();
    }

    /**
     * Get the entry at the given index
     *
     * @param i index of the list to remove
     * @return the index of the added item
     */
    EntryListItem get(int i) {
        if (i >= mUnsorted.size())
            return null;
        return mUnsorted.get(i);
    }

    /**
     * Put a new item at a specified position in the list
     *
     * @param item the item to add
     * @param i    the index of the added item
     */
    void put(int i, EntryListItem item) {
        mUnsorted.add(i, item);
        updateDisplayOrder();
    }

    /**
     * Empty the list
     */
    void clear() {
        mUnsorted.clear();
        mDisplayed.clear();
        mRemoves.clear();
    }

    /**
     * Remove the given item from the list
     *
     * @param item item to remove
     */
    void remove(EntryListItem item, boolean undo) {
        Log.d(TAG, "remove");
        if (undo) {
            if (mRemoves.size() == 0)
                mRemoves.push(new ArrayList<>());
            mRemoves.peek().add(new Remove(mUnsorted.indexOf(item), item));
        }
        mUnsorted.remove(item);
        mDisplayed.remove(item);
    }

    /**
     * Enable/disable edit mode
     */
    void setEditMode(boolean on) {
        mInEditMode = on;
        updateDisplayOrder();
    }

    /**
     * Call to start a new undo set. An undo will undo all the delete operations in the most
     * recent undo set.
     */
    void newUndoSet() {
        mRemoves.push(new ArrayList<>());
    }

    /**
     * Undo the last remove. All operations with the same undo tag will be undone.
     *
     * @return the number of items restored
     */
    int undoRemove() {
        if (mRemoves.size() == 0)
            return 0;
        ArrayList<Remove> items = mRemoves.pop();
        if (items.size() == 0)
            return 0;
        for (Remove it : items)
            mUnsorted.add(it.index, it.item);
        updateDisplayOrder();
        return items.size();
    }

    /**
     * Find an item in the list by text string
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return matched item or null if not found
     */
    EntryListItem findByText(String str, boolean matchCase) {
        for (EntryListItem item : mUnsorted) {
            if (item.getText().equalsIgnoreCase(str))
                return item;
        }
        if (matchCase)
            return null;
        for (EntryListItem item : mUnsorted) {
            if (item.getText().toLowerCase().contains(str.toLowerCase()))
                return item;
        }
        return null;
    }

    /**
     * Find an item in the list by UID
     *
     * @param uid item to find
     * @return matched item or null if not found
     */
    EntryListItem findByUID(long uid) {
        for (EntryListItem item : mUnsorted) {
            if (item.getUID() == uid)
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
    int indexOf(EntryListItem ci) {
        return mUnsorted.indexOf(ci);
    }

    /**
     * Get the index of the item in the sorted list, or -1 if it's not there
     * c.f. findByText(), indexOf
     *
     * @param ci item to get the index of
     * @return the index of the item in the list, or -1 if it's not there
     */
    int indexOfDisplayed(EntryListItem ci) {
        return mDisplayed.indexOf(ci);
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem other) {
        EntryList oth = (EntryList) other;
        if (!oth.getText().equals(getText()) || oth.size() != size())
            return false;

        for (EntryListItem oit : oth.mUnsorted) {
            EntryListItem i = findByText(oit.getText(), true);
            if (i == null || !oit.equals(i))
                return false;
        }
        return true;
    }

    /**
     * Set the item in the list that is currently being moved
     *
     * @param item the item being moved
     */
    void setMovingItem(EntryListItem item) {
        mMovingItem = item;
        if (mArrayAdapter != null)
            mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Inform the UI that the list changed and needs refreshing.
     *
     * @param doSave true to save the list
     */
    public void notifyListChanged(boolean doSave) {
        if (mArrayAdapter != null)
            mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * After an edit to the list, re-order the display representation
     */
    protected void updateDisplayOrder() {
        mDisplayed = (ArrayList<EntryListItem>) mUnsorted.clone();
        if (!mInEditMode && mShowSorted)
            Collections.sort(mDisplayed, (item, item2) -> item.getText().compareToIgnoreCase(item2.getText()));
    }

    /**
     * Launch a thread to perform an asynchronous save to a URI. If there's an error, it will
     * be reported in a Toast on the UI thread.
     *
     * @param uri the URI to save to
     */
    void saveToUri(final Uri uri, Context cxt) {
        // Launch a thread to do this save, so we don't block the ui thread
        Log.d(TAG, "Saving to " + uri);
        final byte[] data;
        try {
            String s = toJSON().toString(1);
            data = s.getBytes();
        } catch (JSONException je) {
            throw new Error("JSON exception " + je.getMessage());
        }
        new Thread(() -> {
            OutputStream stream;
            try {
                String scheme = uri.getScheme();
                if (Objects.equals(scheme, ContentResolver.SCHEME_FILE)) {
                    String path = uri.getPath();
                    stream = new FileOutputStream(new File(path));
                } else if (Objects.equals(scheme, ContentResolver.SCHEME_CONTENT))
                    stream = cxt.getContentResolver().openOutputStream(uri);
                else
                    throw new IOException("Unknown uri scheme: " + uri.getScheme());
                if (stream == null)
                    throw new IOException("Stream open failed");
                stream.write(data);
                stream.close();
                Log.d(TAG, "Saved to " + uri);
            } catch (IOException ioe) {
                final String mess = ioe.getMessage();
                ((Activity) cxt).runOnUiThread(() -> Toast.makeText(cxt, "Exception while saving to Uri " + mess, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * Load the object from JSON or CSV read from the stream
     *
     * @param stream source of the JSON or CSV
     * @throws Exception if something goes wrong
     */
    void fromStream(InputStream stream, Context cxt) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String data;
        StringBuilder sb = new StringBuilder();
        while ((data = br.readLine()) != null)
            sb.append(data).append("\n");
        data = sb.toString();
        try {
            // See if it's JSON

            try {
                fromJSON(new JSONObject(data));
            } catch (JSONException je) {
                // Old format?
                JSONArray ja = new JSONArray(data);
                JSONObject job = new JSONObject();
                job.put("items", ja);
                fromJSON(job);
            }
        } catch (JSONException je) {
            // See if it's CSV...
            try {
                fromCSV(new CSVReader(new StringReader(data)));
            } catch (CsvException csve) {
                throw new Exception("Format error");
            }
        }
    }

    @Override // implements EntryListItem
    public void fromJSON(JSONObject job) throws JSONException {
        mUID = job.getLong("uid");
        try {
            mShowSorted = job.getBoolean("sort");
        } catch (JSONException je) {
            mShowSorted = Settings.getBool(Settings.forceAlphaSort);
        }
    }

    @Override // implements EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject job = new JSONObject();
        JSONArray its = new JSONArray();
        for (EntryListItem cl : mUnsorted)
            its.put(cl.toJSON());
        job.put("uid", mUID);
        job.put("items", its);
        job.put("sort", mShowSorted);
        return job;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        w.writeNext(new String[]{"Item", "Checked"});
        for (EntryListItem it : mUnsorted) {
            it.toCSV(w);
        }
    }

    @Override // implement EntryListItem
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append(getText()).append(":\n");
        for (EntryListItem next : getDisplayOrder()) {
            sb.append(next.toPlainString(tab + "\t")).append("\n");
        }
        return sb.toString();
    }
}
