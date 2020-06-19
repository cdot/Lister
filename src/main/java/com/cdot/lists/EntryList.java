/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

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
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

/**
 * Base class of things that can be serialised to a JSON representation and saved.
 */
abstract class EntryList implements EntryListItem {
    private final String TAG = "EntryList";
    protected ArrayAdapter<EntryListItem> mArrayAdapter;

    protected long mUID;

    // The list that contains this list.
    private EntryList mParent;

    private Context mContext;

    // The basic list
    protected ArrayList<EntryListItem> mUnsorted = new ArrayList<>();

    // Is this list being displayed sorted?
    protected boolean mShowSorted = false;
    // A sorted version of the list (sorts on getText())
    protected ArrayList<EntryListItem> mSorted = new ArrayList<>();

    transient EntryListItem mMovingItem = null;

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
     * @param cxt    the Context, used to access the ContentResolver. Generally the application context.
     */
    EntryList(EntryList parent, Context cxt) {
        mUID = System.currentTimeMillis();
        mParent = parent;
        mContext = cxt;
        mRemoves = new Stack<>();
    }

    /**
     * Construct by reading content from a Uri
     *
     * @param uri    the URI to read from
     * @param parent the list that contains this list (or null for the root)
     * @param cxt    the context, used to access the ContentResolver. Generally the application context.
     * @throws Exception if there's a problem reading or decoding
     */
    EntryList(Uri uri, EntryList parent, Context cxt) throws Exception {
        this(parent, cxt);
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File((uri.getPath())));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = cxt.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
        }
        fromStream(stream);
    }

    @Override // implement EntryListItem
    public long getUID() {
        return mUID;
    }

    @Override // implement EntryListItem
    public EntryList getContainer() {
        return mParent;
    }

    Context getContext() {
        return mContext;
    }

    /**
     * Get an array giving to the current sort order of the items in the list. Used only for
     * display.
     *
     * @return a sorted array of items
     */
    protected ArrayList<EntryListItem> getSorted() {
        return (mShowSorted ? mSorted : mUnsorted);
    }

    /**
     * Save the list, subclasses override if the operation is supported.
     */
    void save(Context cxt) {
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
     * Add a new item to the end of the list
     *
     * @param item the item to add
     */
    void add(EntryListItem item) {
        mUnsorted.add(item);
        reSort();
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
        reSort();
    }

    /**
     * Empty the list
     */
    void clear() {
        mUnsorted.clear();
        mSorted.clear();
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
        mSorted.remove(item);
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
        reSort();
        return items.size();
    }

    /**
     * Find an item in the list by text string
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return index of matched item or -1 if not found
     */
    int findByText(String str, boolean matchCase) {
        int i = -1;
        for (EntryListItem next : mUnsorted) {
            i++;
            if (next.getText().equalsIgnoreCase(str))
                return i;
        }
        if (matchCase)
            return -1;
        i = -1;
        for (EntryListItem next : mUnsorted) {
            i++;
            if (next.getText().toLowerCase().contains(str.toLowerCase()))
                return i;
        }
        return -1;
    }

     /**
     * Find an item in the list by UID
     *
     * @param uid       item to find
     * @return index of matched item or -1 if not found
     */
     int findByUID(long uid) {
        int i = -1;
        for (EntryListItem next : mUnsorted) {
            i++;
            if (next.getUID() == uid)
                return i;
        }
        return -1;
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
    int sortedIndexOf(EntryListItem ci) {
        return mSorted.indexOf(ci);
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem other) {
        EntryList oth = (EntryList)other;
        if (!oth.getText().equals(getText()) || oth.size() != size())
            return false;

        for (EntryListItem oit : oth.mUnsorted) {
            int i = findByText(oit.getText(), true);
            if (i < 0 || !oit.equals(get(i)))
                return false;
        }
        return true;
    }

    /**
     * Move the item to a new position in the list
     *
     * @param item item to move
     * @param i    position to move it to, position in the unsorted list!
     * @return true if the item moved
     */
    boolean moveItemToPosition(EntryListItem item, int i) {
        Log.d(TAG, "M" + i);
        if (i >= 0 && i < mUnsorted.size()) {
            remove(item, false);
            put(i, item);
            return true;
        }
        return false;
    }

    /**
     * Set the item in the list that is currently being moved
     *
     * @param item the item being moved
     */
    void setMovingItem(EntryListItem item) {
        if (mMovingItem == null || item == null)
            mMovingItem = item;
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Inform the UI that the list changed and needs refreshing.
     *
     * @param doSave true to save the list
     */
    public void notifyListChanged(boolean doSave) {
        if (doSave)
            save(getContext());
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * After an edit to the list, re-sort the UI representation
     */
    protected void reSort() {
        mSorted = (ArrayList<EntryListItem>) mUnsorted.clone();
        Collections.sort(mSorted, (item, item2) -> item.getText().compareToIgnoreCase(item2.getText()));
    }

    /**
     * Launch a thread to perform an asynchronous save to a URI. If there's an error, it will
     * be reported in a Toast on the UI thread.
     *
     * @param uri the URI to save to
     */
    void saveToUri(final Uri uri) {
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
                    stream = mContext.getContentResolver().openOutputStream(uri);
                else
                    throw new IOException("Unknown uri scheme: " + uri.getScheme());
                if (stream == null)
                    throw new IOException("Stream open failed");
                stream.write(data);
                stream.close();
                Log.d(TAG, "Saved to " + uri);
            } catch (IOException ioe) {
                final String mess = ioe.getMessage();
                ((Activity) mContext).runOnUiThread(() -> Toast.makeText(mContext, "Exception while saving to Uri " + mess, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * Load the object from JSON or CSV read from the stream
     *
     * @param stream source of the JSON or CSV
     * @throws Exception if something goes wrong
     */
    void fromStream(InputStream stream) throws Exception {
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
        try {
            mUID = job.getLong("uid");
        } catch (JSONException ignore) {
            Log.d(TAG, "WARNING! No UID");
        }
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
        job.put("uid", getUID());
        job.put("items", its);
        job.put("sort", mShowSorted);
        return job;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        w.writeNext(new String[] { "Item", "Checked" });
        for (EntryListItem it : mUnsorted) {
            it.toCSV(w);
        }
    }

    @Override // implement EntryListItem
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append(getText()).append(":\n");
        for (EntryListItem next : getSorted()) {
            sb.append(next.toPlainString(tab + "\t")).append("\n");
        }
        return sb.toString();
    }
}
