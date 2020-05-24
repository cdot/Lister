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

import com.cdot.lists.databinding.ChecklistActivityBinding;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A list of Checklist.
 * Constructed by reading a JSON file from a private storage area
 * On startup, try to read the list from the backing store. Should we wait for the load, or load from
 * the cache and then update? Need to time out backing store reads.
 */
class Checklists extends ArrayList<Checklist> {
    private static final String TAG = "Checklists";

    ItemsArrayAdapter mArrayAdapter;
    Context mContext;

    /**
     * Adapter for the array of items in the list
     */
    class ItemsArrayAdapter extends ArrayAdapter<String> {

        ItemsArrayAdapter() {
            super(mContext, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull
        View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            // Simple text view, so don't muck about with resources
            TextView itemView = (view == null) ? new TextView(mContext) : (TextView) view;
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
            itemView.setText(get(i).mListName);
            return itemView;
        }

        @Override
        public int getCount() {
            return Checklists.this.size();
        }
    }

    /**
     * Constructor
     *
     * @param cxt  Context (Activity) the lists are being used in
     * @param load true to load the list from backing store anc cache
     */
    Checklists(Context cxt, boolean load) {
        mContext = cxt;
        mArrayAdapter = new ItemsArrayAdapter();
        if (load)
            load();
    }

    /**
     * Create a new checklist
     *
     * @param name name of the new checklist
     */
    Checklist createList(String name) {
        Checklist checkList = new Checklist(name, this);
        add(checkList);
        save();
        return checkList;
    }

    /**
     * Create a new checklist from JSON data read from the stream
     *
     * @param stream where to get the data
     * @return the new list
     * @throws Exception IOException or JSONException
     */
    Checklist createList(InputStream stream) throws Exception {
        Checklist checkList = new Checklist(stream, this);
        add(checkList);
        save();
        return checkList;
    }

    /**
     * Make a copy of the list at the given index
     *
     * @param i index of the list to clone
     */
    void cloneListAtIndex(int i) {
        Checklist checklist = new Checklist(get(i), this);
        checklist.mListName += " (copy)";
        add(checklist);
        try {
            save();
        } catch (Exception e) {
            Log.d(TAG, "Exception while saving " + e.getMessage());
        }
    }

    /**
     * Remove the list at the given index
     *
     * @param i index of the list to remove
     */
    void removeListAtIndex(int i) {
        remove(i);
        save();
    }

    /**
     * Find the named list
     *
     * @param name list to search for
     * @return the list if found, or null otherwise
     */
    Checklist findListByName(String name) {
        for (Checklist cl : this)
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
        clear();

        // First reload cache from backing store, if it's available
        Uri uri = Settings.getUri("backingStore");
        if (uri != null) {
            try {
                InputStream stream;
                if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                    stream = new FileInputStream(new File((uri.getPath())));
                } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                    stream = mContext.getContentResolver().openInputStream(uri);
                } else {
                    throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                }
                Log.d(TAG, "Loading lists from " + uri);
                loadFromStream(stream);
            } catch (Exception e) {
                Log.d(TAG, "Exception loading backing store " + e);
            }
        }

        // Reload from cache any lists that are stamped more recent than what was read from
        // the backing store; or, if the backing store wasn't available, get lists from here.
        // Note this could result in confusion if an list is updated on the backing store
        // asynchronously. No attempt is made to resolve conflicts
        Checklists cache = new Checklists(mContext, false);
        try {
            FileInputStream fis = mContext.openFileInput(Settings.cacheFile);
            Log.d(TAG, "Loading lists from cache");
            cache.loadFromStream(fis);
            int added = 0;
            for (Checklist cl : cache) {
                Checklist known = findListByName(cl.mListName);
                if (known == null || cl.mTimestamp > known.mTimestamp) {
                    if (known != null)
                        remove(known);
                    add(new Checklist(cl, this));
                    added++;
                }
            }
            Log.d(TAG, "Added " + added + " lists from cache");
        } catch (Exception e) {
            Log.d(TAG, "Exception mergeing from cache " + e);
        }
        if (mArrayAdapter != null)
            mArrayAdapter.notifyDataSetChanged();
    }

    void saveToUri(final Uri uri) {
        // Launch a thread to do this save, so we don't block the ui thread
        Log.d(TAG, "Saving to backing store");
        final byte[] data = toJSON().toString().getBytes();
        new Thread(new Runnable() {
            public void run() {
                OutputStream stream;
                try {
                    String scheme = uri.getScheme();
                    if (Objects.equals(scheme, ContentResolver.SCHEME_FILE)) {
                        String path = uri.getPath();
                        stream = new FileOutputStream(new File(path));
                    } else if (Objects.equals(scheme, ContentResolver.SCHEME_CONTENT))
                        stream = mContext.getContentResolver().openOutputStream(uri);
                    else
                        throw new Error("Failed to save lists. Unknown uri scheme: " + uri.getScheme());
                    stream.write(data);
                    stream.close();
                    Log.d(TAG, "Saved to backing store");
                } catch (IOException ioe) {
                    throw new Error( "Exception while saving to backing store " + ioe.getMessage());
                }
            }
        }).start();
    }

    /**
     * Load the checklists from JSON read from the stream
     *
     * @param stream source of the JSON
     * @throws Exception IOException or JSONException
     */
    private void loadFromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        loadFromJSON(new JSONArray(sb.toString()));
    }

    /**
     * Process a JSON array object and load the lists found therein
     *
     * @param lists array of checklists in JSON format
     * @throws JSONException if it can't be analysed e.g. missing fields
     */
    private void loadFromJSON(JSONArray lists) throws JSONException {
        clear();
        for (int i = 0; i < lists.length(); i++) {
            add(new Checklist(lists.getJSONObject(i), this));
        }
        Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
    }

    /**
     * Construct a JSON array object from the checklists we manage
     * @return an array of JSON checklist objects
     */
    private JSONArray toJSON()  {
        JSONArray json = new JSONArray();
        for (Checklist cl : this)
            json.put(cl.toJSON());
        return json;
    }

    /**
     * Save the lists to backing store, if one is configured.
     */
    private void saveToBackingStore() {
        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        saveToUri(uri);
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
        saveToBackingStore();
    }
}
