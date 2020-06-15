/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
            Checklist item = (Checklist) getSorted().get(i);
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
            return size();
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
            load(cxt);
    }

    @Override // implement EntryListItem
    public void setText(String s) {
        throw new Error("Unexpected setText in " + TAG);
    }

    @Override // implement EntryListItem
    public String getText() {
        throw new Error("Unexpected getText in " + TAG);
    }

    /**
     * Make a copy of the list at the given index
     *
     * @param i index of the list to clone
     */
    void cloneList(EntryListItem i, Context cxt) {
        Checklist checklist = new Checklist((Checklist) i, this, cxt);
        String newname = checklist.getText() + " (copy)";
        checklist.setText(newname);
        add(checklist);
        notifyListChanged(true);
    }

    /**
     * Load the list of checklists. The backing store has the master list, the local cache
     * is merged with it, replacing the lists on the backing store if the last save date on the
     * cache is more recent.
     */
    void load(final Context cxt) {
        // First load the cache, then asynchronously load the backing store and update the list
        // if it has changed
        try {
            FileInputStream fis = cxt.openFileInput(Settings.cacheFile);
            fromStream(fis);
        } catch (Exception e) {
            Log.d(TAG, "Exception loading from cache " + Settings.cacheFile + ": " + e);
        }
        Log.d(TAG, size() + " lists loaded from cache");

        // <DEBUG>
        /*for (int i = 0; i < size(); i++) {
            for (int j = 1; j < size(); ) {
                if (get(j).getText().equals(get(i).getText())) {
                    Log.d(TAG, "REMOVE DUPLICATE " + get(j).getText());
                    remove(get(j), false);
                } else
                    j++;
            }
        }
        reSort();*/
        // </DEBUG>

        mArrayAdapter.notifyDataSetChanged();

        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Starting load thread");
                try {
                    Checklists backing = new Checklists(cxt, false);
                    InputStream stream;
                    if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                        stream = new FileInputStream(new File((uri.getPath())));
                    } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                        stream = cxt.getContentResolver().openInputStream(uri);
                    } else {
                        throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                    }
                    backing.fromStream(stream);
                    Log.d(TAG, backing.size() + " lists loaded from backing store");

                    boolean changed = false; // will the list require a save?
                    for (EntryListItem it : backing.mUnsorted) {
                        Checklist cl = (Checklist) it;
                        int idx = find(cl.getText(), true);
                        Checklist known = idx < 0 ? null : (Checklist) get(idx);
                        if (known == null || cl.mTimestamp > known.mTimestamp) {
                            if (known != null) {
                                if (known.merge(cl))
                                    changed = true;
                            } else {
                                add(new Checklist(cl, Checklists.this, cxt));
                                changed = true;
                            }
                        }
                    }
                    final boolean save = changed;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            notifyListChanged(save);
                        }
                    });
                } catch (final SecurityException se) {
                    Log.d(TAG, "Security Exception loading backing store " + se);
                    // run on UI thread
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            Toast.makeText(getContext(), "Security Exception loading backing store " + se, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG, "Exception loading backing store: " + e);
                }
            }
        }).start();
    }

    @Override // EntryList
    public void fromJSON(JSONObject job) throws JSONException {
        super.fromJSON(job);
        JSONArray lists = job.getJSONArray("items");
        for (int i = 0; i < lists.length(); i++)
            add(new Checklist(lists.getJSONObject(i), this, getContext()));
        Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
    }

    @Override // EntryList
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        for (EntryListItem item : getSorted()) {
            sb.append(tab).append(item.getText()).append(":\n");
            sb.append(item.toPlainString(tab + "\t")).append("\n");
        }
        return sb.toString();
    }

    /**
     * Save the checklists. Saves to the cache first, and then the backing store (if configured)
     */
    void save(Context cxt) {
        // Save to the cache, then refresh the backing store. That way the
        // cache will always be older than the backing store if the backing store save
        // succeeds
        try {
            FileOutputStream stream = cxt.openFileOutput(Settings.cacheFile, Context.MODE_PRIVATE);
            String s = this.toJSON().toString(1);
            stream.write(s.getBytes());
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
