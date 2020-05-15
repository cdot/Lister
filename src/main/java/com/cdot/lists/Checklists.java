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
import java.net.URLDecoder;
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
    private Context mContext;

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

    Checklists(Context cxt) {
        mContext = cxt;
    }

    Checklist newList(String name) {
        return new Checklist(name, mContext);
    }

    /**
     * Load the list of checklists from the cache
     * The cache directory contains lists, one per .json file
     * The backing store contains a single file with all lists, stored as .json
     */
    void load() {
        clear();

        // First reload cache from backing store, if it's available
        Uri bs = Settings.getUri("backingStore");
        if (bs != null)
            try {
                loadFromBackingStore(bs);
                for (Checklist cl : this)
                    cl.saveToCache();
                clear();
                // If we returned here, we'd be ignoring lists that are local but haven't made
                // it to the backing store yet. So drop through and reload from the cache.
            } catch (Exception e) {
                Log.d(TAG, "Exception loading " + bs + ": " + e);
            }

        loadFromCache();

        if (mArrayAdapter == null)
            mArrayAdapter.notifyDataSetChanged();
    }

    private void loadFromCache() {
        // Load list of lists from cache
        String[] split = mContext.fileList();
        for (String s : split) {
            if (s.length() > 0) {
                try {
                    s = URLDecoder.decode(s, "UTF-8");
                    Checklist list = new Checklist(s, mContext);
                    add(list);
                } catch (Exception e) {
                    Log.d(TAG, "Exception loading " + s + ": " + e);
                }
            }
        }
    }

    private void loadFromBackingStore(Uri uri) throws Exception {
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File((uri.getPath())));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = mContext.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
        }
        loadFromStream(stream);
    }


    private void saveToBackingStore(final Uri uri) {
        // Launch a thread to do this save
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
                } catch (Exception ioe) {
                    Log.d(TAG, "Exception while saving " + ioe.getMessage());
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
            add(new Checklist(lists.getJSONObject(i)));
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
        saveCache();
    }

    private void saveCache() {
        // Save to the cache, then refresh the backing store from the cache
        for (Checklist cl : this) {
            cl.saveToCache();
        }

        Uri bs = Settings.getUri("backingStore");
        if (bs != null)
            try {
                saveToBackingStore(bs);
            } catch (Exception e) {
                Log.d(TAG, "Exception loading " + bs + ": " + e);
            }
    }

    void cloneChecklistAtIndex(int i) {
        try {
            Checklist checkList = new Checklist(get(i));
            add(checkList);
            checkList.saveToCache();
        } catch (Exception ignored) {
        }
    }
}
