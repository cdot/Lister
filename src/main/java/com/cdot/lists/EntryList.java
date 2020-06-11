/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

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
import java.util.Objects;

/**
 * base class of things that can be serialised to a JSON representation and saved.
 */
abstract class EntryList implements JSONable {
    private final String TAG = "Serialisable";
    ArrayAdapter mArrayAdapter;

    private EntryList mParent;
    private Context mContext;

    transient EntryListItem mMovingItem = null;

    EntryList(EntryList parent, Context cxt) {
        mParent = parent;
        mContext = cxt;
    }

    EntryList(InputStream stream, EntryList parent, Context cxt) throws Exception {
        this(parent, cxt);
        fromStream(stream);
    }

    public EntryList getContainer() {
        return mParent;
    }

    Context getContext() {
        return mContext;
    }

    /**
     * Save the list, if the operation is supported.
     */
    void save() {}

    String getName() {
        return "none";
    }

    void setName(String name) {
    }

    /**
     * Get the current list size
     *
     * @return size
     */
    abstract int size();

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     * @return the index of the added item
     */
    abstract int add(EntryListItem item);

    /**
     * Remove the given item from the list
     *
     * @param idx index of the item to remove
     */
    abstract void remove(int idx);

    void remove(EntryListItem item) {
        remove(indexOf(item));
    }

    /**
     * Find an item in the list
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return index of matched item or -1 if not found
     */
    abstract int find(String str, boolean matchCase);

    /**
     * Get the entry at the given index
     *
     * @param i index of the list to remove
     */
    abstract EntryListItem get(int i);

    /**
     * Construct by reading content from a Uri
     *
     * @param uri the URI to read from
     * @param cxt the context, used to access the ContentResolver. Generally the application context.
     * @throws Exception if there's a problem reading or decoding
     */
    EntryList(Uri uri, EntryList parent, Context cxt) throws Exception {
        this(parent, cxt);
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File((uri.getPath())));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = mContext.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
        }
        fromStream(stream);
    }

    /**
     * Get the index of the item in the list, or -1 if it's not there
     * c.f. find()
     * @param ci
     * @return the index of the item in the list, or -1 if it's not there
     */
    abstract int indexOf(EntryListItem ci);

    /**
     * Move the item to a new position in the list
     * @param item item to move
     * @param i position to move it to
     */
    abstract void moveItemToPosition(EntryListItem item, int i);

    void setMovingItem(EntryListItem item) {
        if (mMovingItem == null || item == null)
            mMovingItem = item;
        mArrayAdapter.notifyDataSetChanged();
    }

    void notifyListChanged() {
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Launch a thread to perform an asynchronous save to a URI. If there;s an error, it will
     * be reported in a Toast on the UI thread.
     *
     * @param uri the URI to save to
     */
    void saveToUri(final Uri uri) {
        // Launch a thread to do this save, so we don't block the ui thread
        Log.d(TAG, "Saving to " + uri);
        final byte[] data;
        try {
            data = toJSON().toString().getBytes();
        } catch (JSONException je) {
            throw new Error("JSON exception " + je.getMessage());
        }
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
                        throw new IOException("Unknown uri scheme: " + uri.getScheme());
                    if (stream == null)
                        throw new IOException("Stream open failed");
                    stream.write(data);
                    stream.close();
                    Log.d(TAG, "Saved to " + uri);
                } catch (IOException ioe) {
                    final String mess = ioe.getMessage();
                    ((Activity) mContext).runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(mContext, "Exception while saving to Uri " + mess, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Load the object from JSON read from the stream
     *
     * @param stream source of the JSON
     * @throws Exception IOException or JSONException
     */
    void fromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        fromJSON(new JSONArray(sb.toString()));
    }
}
