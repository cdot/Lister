/*
  Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * A list of Checklist.
 * Constructed by reading a JSON file from a private storage area
 * On startup, try to read the list from the backing store. Should we wait for the load, or load from
 * the cache and then update? Need to time out backing store reads.
 */
class Checklists extends EntryList {
    private static final String TAG = "Checklists";

    private Context mContext;

    /**
     * Constructor
     *
     * @param cxt  Context used for saving
     */
    Checklists(Context cxt) {
        super(null);
        mContext = cxt;
    }

    @Override // EntryList
    public EntryListItemView makeItemView(EntryListItem item, Context cxt) {
        return new ChecklistsItemView(item, false, cxt);
    }

    @Override // implement EntryListItem
    public void setText(String s) {
        throw new Error("Unexpected setText in " + TAG);
    }

    @Override // implement EntryListItem
    public String getText() {
        throw new Error("Unexpected getText in " + TAG);
    }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return false;
    }

    // Called on the cache to merge the backing list
    @Override // EntryListItem
    public boolean merge(EntryListItem backing) {
        Checklists backLists = (Checklists) backing;
        boolean changed = false; // will the list require a save?
        for (EntryListItem backIt : backLists.mUnsorted) {
            Checklist backList = (Checklist) backIt;
            Checklist cacheList = (Checklist) findByUID(backList.getUID());
            if (cacheList != null) {
                if (cacheList.merge(backList))
                    changed = true;
            } else {
                Checklist newList = new Checklist(this, backList);
                add(newList);
                changed = true;
            }
        }
        return changed;
    }

    @Override // EntryList
    public void fromJSON(JSONObject job) throws JSONException {
        super.fromJSON(job);
        JSONArray lists = job.getJSONArray("items");
        for (int i = 0; i < lists.length(); i++)
            add(new Checklist(this, lists.getJSONObject(i)));
        Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
    }

    @Override // EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        throw new Exception("Unable to read multiple lists from CSV");
    }

    @Override // EntryList
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        for (EntryListItem item : getDisplayOrder()) {
            sb.append(tab).append(item.getText()).append(":\n");
            sb.append(item.toPlainString(tab + "\t")).append("\n");
        }
        return sb.toString();
    }

    /**
     * Make a copy of the list at the given index
     *
     * @param i index of the list to clone
     */
    void cloneList(EntryListItem i) {
        Checklist checklist = new Checklist(this, (Checklist) i);
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
    Thread mLoadThread = null;

    void load(final AppCompatActivity cxt) {
        if (mLoadThread != null && mLoadThread.isAlive()) {
            mLoadThread.interrupt();
            mLoadThread = null;
        }

        clear();

        // First load the cache, then asynchronously load the backing store
        try {
            FileInputStream fis = cxt.openFileInput(Settings.cacheFile);
            fromStream(fis, cxt);
        } catch (Exception e) {
            Log.d(TAG, "Exception loading from cache " + Settings.cacheFile + ": " + e);
        }
        Log.d(TAG, size() + " lists loaded from cache");
        //removeDuplicates();
        notifyListChanged(false);

        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        mLoadThread = new Thread(() -> {
            Log.d(TAG, "Starting load thread");
            try {
                Checklists backing = new Checklists(cxt);
                InputStream stream;
                if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                    stream = new FileInputStream(new File((uri.getPath())));
                } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                    stream = cxt.getContentResolver().openInputStream(uri);
                } else {
                    throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                }
                if (mLoadThread.isInterrupted())
                    return;
                backing.fromStream(stream, cxt);
                Log.d(TAG, backing.size() + " lists loaded from backing store");
                clear(); // kill the cache
                boolean save = merge(backing);
                //removeDuplicates();
                new Handler(Looper.getMainLooper()).post(() -> notifyListChanged(save));
            } catch (final SecurityException se) {
                // openInputStream denied, reroute through picker to re-establish permissions
                Log.d(TAG, "Security Exception loading backing store " + se);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
                        builder.setTitle(R.string.access_denied);
                        builder.setMessage(R.string.reconfirm_backing_store);
                        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            // Callers must include CATEGORY_OPENABLE in the Intent to obtain URIs that can be opened with ContentResolver#openFileDescriptor(Uri, String)
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            // Indicate the permissions should be persistable across device reboots
                            intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            if (Build.VERSION.SDK_INT >= 26)
                                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                            intent.setType("application/json");
                            cxt.startActivityForResult(intent, Settings.REQUEST_CHANGE_STORE);
                            // onActivityResult will re-load the list in response to a successful
                            // REQUEST_CHANGE_STORE
                        });
                        builder.show();
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Exception loading backing store: " + e);
            }
        });
        mLoadThread.start();
    }

    // DEBUG ONLY
    private void removeDuplicates() {
        for (int i = 0; i < size(); i++) {
            EntryListItem ei = get(i);
            for (int j = i + 1; j < size(); ) {
                if (get(j).equals(ei)) {
                    Log.d(TAG, "REMOVE DUPLICATE " + get(j).getText());
                    remove(get(j), false);
                } else
                    j++;
            }
        }
        updateDisplayOrder();
    }

    public void notifyListChanged(boolean save) {
        super.notifyListChanged(save);
        if (save)
            saveList(mContext);
    }

    /**
     * Save the checklists. Saves to the cache first, and then the backing store (if configured)
     * @param cxt context of the save
     */
    void saveList(Context cxt) {
        // Save to the cache, then refresh the backing store. That way the
        // cache will always be older than the backing store if the backing store save
        // succeeds
        Log.d(TAG, "Saving to cache");
        try {
            FileOutputStream stream = cxt.openFileOutput(Settings.cacheFile, Context.MODE_PRIVATE);
            String s = this.toJSON().toString(1);
            stream.write(s.getBytes());
            stream.close();
        } catch (Exception e) {
            // Would really like to toast this
            Log.d(TAG, "Exception saving to cache: " + e);
        }
        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        saveToUri(uri, cxt);
    }
}
