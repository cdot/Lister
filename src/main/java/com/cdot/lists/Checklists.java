package com.cdot.lists;

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

    private ItemsArrayAdapter mArrayAdapter;
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
            itemView.setText(get(i).getListName());
            return itemView;
        }

        @Override
        public int getCount() {
            return Checklists.this.size();
        }
    }

    Checklists(Context cxt, boolean load) {
        mContext = cxt;
        if (load)
            load();
    }

    Checklist createList(String name) {
        Checklist checkList = new Checklist(name, this);
        add(checkList);
        save();
        return checkList;
    }

    Checklist createList(InputStream stream) throws Exception {
        Checklist checkList = new Checklist(stream, this);
        add(checkList);
        save();
        return checkList;
    }

    void cloneChecklistAtIndex(int i) {
        try {
            Checklist checkList = new Checklist(get(i), this);
            checkList.setListName(checkList.getListName()  + " (copy)");
            add(checkList);
            save();
        } catch (Exception ignored) {
        }
    }

    Checklist find(String name) {
        for (Checklist cl : this)
            if (cl.getListName().equals(name))
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
                if (Objects.equals(uri.getScheme(), "file")) {
                    stream = new FileInputStream(new File((uri.getPath())));
                } else if (Objects.equals(uri.getScheme(), "content")) {
                    stream = mContext.getContentResolver().openInputStream(uri);
                } else {
                    throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                }
                // If we returned here, we'd be ignoring lists that are local but haven't made
                // it to the backing store yet. So drop through and reload from the cache.
            } catch (Exception e) {
                Log.d(TAG, "Exception loading backing store " + e);
            }
        }

        Checklists cache = new Checklists(mContext, false);
        try {
            FileInputStream fis = mContext.openFileInput("checklists.json");
            cache.loadFromStream(fis);
            for (Checklist cl : cache) {
                Checklist known = find(cl.getListName());
                if (known == null || cl.mTimestamp > known.mTimestamp) {
                    add(new Checklist(cl, this));
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception mergeing from cache " + e);
        }
        if (mArrayAdapter != null)
            mArrayAdapter.notifyDataSetChanged();
    }

    private void saveToBackingStore() {
        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        // Launch a thread to do this save, so we don't block the ui thread
        new Thread(new Runnable() {
            public void run() {
                OutputStream stream;
                try {
                    if (Objects.equals(uri.getScheme(), "file"))
                        stream = new FileOutputStream(new File((uri.getPath())));
                    else if (Objects.equals(uri.getScheme(), "content"))
                        stream = mContext.getContentResolver().openOutputStream(uri, "w");
                    else
                        throw new IOException("Failed to save lists. Unknown uri scheme: " + uri.getScheme());
                    stream.write(toJSON().toString().getBytes());
                    stream.close();
                    Log.d(TAG, "Saved to backing store");
                } catch (Exception ioe) {
                    Log.d(TAG, "Exception while saving to backing store " + ioe.getMessage());
                }
            }
        }).start();
    }

    private void loadFromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        loadFromJSON(new JSONArray(sb.toString()));
    }

    private void loadFromJSON(JSONArray lists) throws JSONException {
        clear();
        for (int i = 0; i < lists.length(); i++) {
            add(new Checklist(lists.getJSONObject(i), this));
        }
    }

    private JSONArray toJSON() throws JSONException {
        JSONArray json = new JSONArray();
        for (Checklist cl : this) {
            json.put(cl.toJSON());
        }
        return json;
    }

    ItemsArrayAdapter getArrayAdapter() {
        mArrayAdapter = new ItemsArrayAdapter();
        return mArrayAdapter;
    }

    void setToaster(Context t) {
        mContext = t;
    }

    void removeChecklistAtIndex(int i) {
        remove(i);
        save();
    }

    void save() {
        // Save to the cache, then refresh the backing store. That way the
        // cache will always be older than the backing store if the backing store save
        // succeeds
        try {
            FileOutputStream stream = mContext.openFileOutput("checklists.json", Context.MODE_PRIVATE);
            stream.write(this.toJSON().toString().getBytes());
            stream.close();
        } catch (Exception e) {
            // Would really like to toast this
            Log.d(TAG, "Exception saving to backing store " + e);
        }
        ;
        saveToBackingStore();
    }
}
