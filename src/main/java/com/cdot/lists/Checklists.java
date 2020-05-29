package com.cdot.lists;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A list of Checklist.
 * Constructed by reading a JSON file from a private storage area
 * On startup, try to read the list from the backing store. Should we wait for the load, or load from
 * the cache and then update? Need to time out backing store reads.
 */
class Checklists extends Serialisable {
    private static final String TAG = "Checklists";

    private ArrayList<Checklist> mLists = new ArrayList<>();

    // Adapter in the context where this Checklist is being used.
    ArrayAdapter mArrayAdapter;

    /**
     * Adapter for the array of items in the list
     */
    class ItemsArrayAdapter extends ArrayAdapter<String> {

        ItemsArrayAdapter(Context cxt) {
            super(cxt, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull
        View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            // Simple text view, so don't muck about with resources
            TextView itemView = (view == null) ? new TextView(this.getContext()) : (TextView) view;
            switch (Settings.getInt("textSizeIndex")) {
                case Settings.TEXT_SIZE_SMALL:
                    itemView.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                    //mVerticalPadding = 0;
                    break;
                case Settings.TEXT_SIZE_MEDIUM:
                case Settings.TEXT_SIZE_DEFAULT:
                    itemView.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                    //mVerticalPadding = 5;
                    break;
                case Settings.TEXT_SIZE_LARGE:
                    itemView.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                    //mVerticalPadding = 10;
                    break;
            }
            itemView.setText(mLists.get(i).mListName);
            return itemView;
        }

        @Override
        public int getCount() {
            return mLists.size();
        }
    }

    /**
     * Constructor
     *
     * @param cxt  Context (Activity) the lists are being used in
     * @param load true to load the list from cache (and trigger an update from backing store)
     */
    Checklists(Context cxt, boolean load) {
        super(cxt);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
        if (load)
            load();
    }

    /**
     * Add a checklist and save
     *
     * @param list list to add
     */
    Checklist addList(Checklist list) {
        mLists.add(list);
        notifyListsChanged();
        return list;
    }

    /**
     * Get the list at the given index
     *
     * @param i index of the list to remove
     */
    Checklist getListAt(int i) {
        return mLists.get(i);
    }

    /**
     * Get the current list size
     * @return size
     */
    int size() {
        return mLists.size();
    }

    /**
     * Make a copy of the list at the given index
     *
     * @param i index of the list to clone
     */
    void cloneListAt(int i) {
        Checklist checklist = new Checklist(mLists.get(i), this);
        checklist.mListName += " (copy)";
        mLists.add(checklist);
        notifyListsChanged();
    }

    /**
     * Remove the list at the given index
     *
     * @param i index of the list to remove
     */
    void removeListAt(int i) {
        mLists.remove(i);
        notifyListsChanged();
    }

    /**
     * Find the named list
     *
     * @param name list to search for
     * @return the list if found, or null otherwise
     */
    Checklist findListByName(String name) {
        for (Checklist cl : mLists)
            if (cl.mListName.equals(name))
                return cl;
        return null;
    }

    /**
     * Load the list of checklists. The backing store has the master list, the local cache
     * is merged with it, replacing the lists on the backing store if the last save date on the
     * cache is more recent.
     */
    void load() {
        mLists.clear();

        // First load the cache, then asynchronously load the backing store and update the list
        // if it has changed
        try {
            FileInputStream fis = mContext.openFileInput(Settings.cacheFile);
            fromStream(fis);
        } catch (Exception e) {
            Log.d(TAG, "Exception mergeing from cache " + e);
        }

        mArrayAdapter.notifyDataSetChanged();

        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    Checklists backing = new Checklists(mContext, false);
                    InputStream stream;
                    if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                        stream = new FileInputStream(new File((uri.getPath())));
                    } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                        stream = mContext.getContentResolver().openInputStream(uri);
                    } else {
                        throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                    }
                    backing.fromStream(stream);
                    boolean changed = false;
                    for (Checklist cl : backing.mLists) {
                        Checklist known = findListByName(cl.mListName);
                        if (known == null || cl.mTimestamp > known.mTimestamp) {
                            if (known != null) {
                                if (known.merge(cl))
                                    changed = true;
                            } else {
                                mLists.add(new Checklist(cl, Checklists.this));
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        notifyListsChanged();
                        Log.d(TAG, "Updated lists from " + uri);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Exception loading backing store " + e);
                }
            }
        }).start();
    }

    void notifyListsChanged() {
        save();
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Process a JSON array object and load the lists found therein
     *
     * @param jo array of checklists in JSON format
     * @throws JSONException if it can't be analysed e.g. missing fields
     */
    public void fromJSON(Object jo) throws JSONException {
        JSONArray lists = (JSONArray)jo;
        mLists.clear();
        for (int i = 0; i < lists.length(); i++) {
            mLists.add(new Checklist(lists.getJSONObject(i), this));
        }
        Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
    }

    /**
     * Construct a JSON array object from the checklists we manage
     *
     * @return an array of JSON checklist objects
     */
    Object toJSON() throws JSONException {
        JSONArray json = new JSONArray();
        for (Checklist cl : mLists)
            json.put(cl.toJSON());
        return json;
    }

    /**
     * Save the checklists. Saves to the cache first, and then the backing store (if configured)
     */
    void save() {
        // Save to the cache, then refresh the backing store. That way the
        // cache will always be older than the backing store if the backing store save
        // succeeds
        try {
            FileOutputStream stream = mContext.openFileOutput(Settings.cacheFile, Context.MODE_PRIVATE);
            stream.write(this.toJSON().toString().getBytes());
            stream.close();
        } catch (Exception e) {
            // Would really like to toast this
            Log.d(TAG, "Exception saving to cache " + e);
        }
        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        saveToUri(uri);
    }
}
