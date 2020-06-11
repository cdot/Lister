/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

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
class Checklists extends EntryList {
    private static final String TAG = "Checklists";

    private ArrayList<Checklist> mLists = new ArrayList<>();

    /**
     * Adapter for the array of lists in the list
     */
    class ListsArrayAdapter extends ArrayAdapter<String> {

        ListsArrayAdapter(Context cxt) {
            super(cxt, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull
        View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            Checklist item = mLists.get(i);
            ChecklistsItemView itemView;
            if (view == null) {
                assert item != null;
                itemView = new ChecklistsItemView(item, false, getContext());
            } else {
                itemView = (ChecklistsItemView) view;
                // TODO: it this really required? Surely the new should have dealt with it
                itemView.setItem(item);
            }
            itemView.updateView();
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
        super(null, cxt);
        mArrayAdapter = new ListsArrayAdapter(cxt);
        if (load)
            load();
    }

    // @implements EntryList
    @Override
    int add(EntryListItem list) {
        mLists.add((Checklist)list);
        notifyListChanged();
        return mLists.indexOf(list);
    }

    // @implements EntryList
    @Override
    int find(@NonNull String name, boolean matchCase) {
        int i = 0;
        for (Checklist e : mLists) {
            if (name.equals(e.getName()))
                return i;
            i++;
        }
        return -1;
    }

    // Implements EntryList
    @Override
    EntryListItem get(int i) {
        return mLists.get(i);
    }

    // Implements EntryList
    @Override
    int size() {
        return mLists.size();
    }

    // Implements EntryList
    @Override
    int indexOf(EntryListItem ci) {
        return mLists.indexOf(ci);
    }

    // Implements EntryList
    @Override
    void moveItemToPosition(EntryListItem item, int i) {
        if (i >= 0 && i <= mLists.size() - 1) {
            mLists.remove(item);
            mLists.add(i, (Checklist)item);
        }
    }

    /**
     * Make a copy of the list at the given index
     *
     * @param i index of the list to clone
     */
    void cloneListAt(int i) {
        Checklist checklist = new Checklist(mLists.get(i), this, getContext());
        String newname = checklist.getName() + " (copy)";
        checklist.setName(newname);
        mLists.add(checklist);
        notifyListChanged();
    }

    /**
     * Remove the list at the given index
     *
     * @param i index of the list to remove
     */
    // implements EntryList
    @Override
    void remove(int i) {
        mLists.remove(i);
        notifyListChanged();
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
            FileInputStream fis = getContext().openFileInput(Settings.cacheFile);
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
                    Checklists backing = new Checklists( getContext(), false);
                    InputStream stream;
                    if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                        stream = new FileInputStream(new File((uri.getPath())));
                    } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                        stream = getContext().getContentResolver().openInputStream(uri);
                    } else {
                        throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                    }
                    backing.fromStream(stream);
                    boolean changed = false;
                    for (Checklist cl : backing.mLists) {
                        int idx = find(cl.getName(), true);
                        Checklist known = idx < 0 ? null : mLists.get(idx);
                        if (known == null || cl.mTimestamp > known.mTimestamp) {
                            if (known != null) {
                                if (known.merge(cl))
                                    changed = true;
                            } else {
                                mLists.add(new Checklist(cl, Checklists.this, getContext()));
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        notifyListChanged();
                        Log.d(TAG, "Updated lists from " + uri);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Exception loading backing store " + e);
                }
            }
        }).start();
    }

    @Override // EntryList
    void notifyListChanged() {
        save();
        mArrayAdapter.notifyDataSetChanged();
    }

    @Override // JSONable
    public void fromJSON(Object jo) throws JSONException {
        JSONArray lists = (JSONArray)jo;
        mLists.clear();
        for (int i = 0; i < lists.length(); i++) {
            mLists.add(new Checklist(lists.getJSONObject(i), this, getContext()));
        }
        Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
    }

    @Override // JSONable
    public Object toJSON() throws JSONException {
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
            FileOutputStream stream = getContext().openFileOutput(Settings.cacheFile, Context.MODE_PRIVATE);
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
