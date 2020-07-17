/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.model;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.view.EntryListItemView;
import com.cdot.lists.MainActivity;
import com.cdot.lists.Settings;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

/**
 * Base class of things that can be serialised to a JSON representation and saved.
 */
public abstract class EntryList extends EntryListItem {
    private final String TAG = "EntryList";

    // The list that contains this list.
    private EntryList mParent;

    // The basic list
    private ArrayList<EntryListItem> mData = new ArrayList<>();

    // Is this list being displayed sorted?
    private boolean mShownSorted = false;

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
        super();
        mParent = parent;
        mRemoves = new Stack<>();
    }

    EntryList(EntryList parent, EntryList copy) {
        super(copy);
        mParent = parent;
        mRemoves = new Stack<>();
        mShownSorted = copy.isShownSorted();
    }

    MainActivity getMainActivity() {
        return mParent.getMainActivity();
    }

    public List<EntryListItem> getData() {
        return mData;
    }

    public List<EntryListItem> cloneItemList() {
        return (List<EntryListItem>) mData.clone();
    }

    @Override // EntryListItem
    public EntryList getContainer() {
        return mParent;
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
        notifyListChanged(true);
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
        notifyListChanged(true);
    }

    /**
     * Empty the list
     */
    public void clear() {
        mData.clear();
        mRemoves.clear();
        notifyListChanged(true);
    }

    public int getRemoveCount() {
        return mRemoves.size();
    }

    public boolean isShownSorted() {
        return mShownSorted;
    }

    public void toggleShownSorted() {
        mShownSorted = !mShownSorted;
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
        notifyListChanged(true);
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
     * Find an item in the list by UID
     *
     * @param uid item to find
     * @return matched item or null if not found
     */
    public EntryListItem findByUID(long uid) {
        for (EntryListItem item : mData) {
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
    public int indexOf(EntryListItem ci) {
        return mData.indexOf(ci);
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem other) {
        EntryList oth = (EntryList) other;
        if (!oth.getText().equals(getText()) || oth.size() != size())
            return false;

        for (EntryListItem oit : oth.mData) {
            EntryListItem i = findByText(oit.getText(), true);
            if (i == null || !oit.equals(i))
                return false;
        }
        return true;
    }

    /**
     * Launch a thread to perform an asynchronous save to a URI. If there's an error, it will
     * be reported in a Toast on the UI thread.
     *
     * @param uri the URI to save to
     */
    public void saveToUri(final Uri uri, Context cxt) {
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
    public void fromStream(InputStream stream) throws Exception {
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
            mShownSorted = job.getBoolean("sort");
        } catch (JSONException je) {
            mShownSorted = getMainActivity().getSettings().getBool(Settings.forceAlphaSort);
        }
    }

    @Override // implements EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject job = new JSONObject();
        JSONArray its = new JSONArray();
        for (EntryListItem cl : mData)
            its.put(cl.toJSON());
        job.put("uid", getUID());
        job.put("items", its);
        job.put("sort", mShownSorted);
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
}
