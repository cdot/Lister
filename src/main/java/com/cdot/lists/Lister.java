/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Application singleton that handles the lists and preferences
 */
public class Lister extends Application {
    public static final String TAG = Lister.class.getSimpleName();

    // Shared Preferences
    public static final String PREF_ALWAYS_SHOW = "showListInFrontOfLockScreen";
    public static final String PREF_GREY_CHECKED = "greyCheckedItems";
    public static final String PREF_ENTIRE_ROW_TOGGLES = "entireRowTogglesItem";
    public static final String PREF_LAST_STORE_FAILED = "lastStoreSaveFailed";
    public static final String PREF_LEFT_HANDED = "checkBoxOnLeftSide";
    public static final String PREF_STAY_AWAKE = "stayAwake";
    public static final String PREF_STRIKE_CHECKED = "strikeThroughCheckedItems";
    public static final String PREF_TEXT_SIZE_INDEX = "textSizeIndex";
    public static final String PREF_URI = "backingStore";

    public static final String CACHE_FILE = "checklists.json";

    // Must match res/values/strings.xml/text_size_list
    public static final int TEXT_SIZE_DEFAULT = 0;
    public static final int TEXT_SIZE_SMALL = 1;
    public static final int TEXT_SIZE_MEDIUM = 2;
    public static final int TEXT_SIZE_LARGE = 3;

    private final static Map<String, Boolean> sBoolDefaults = new HashMap<String, Boolean>() {{
        put(PREF_ALWAYS_SHOW, false);
        put(PREF_GREY_CHECKED, true);
        put(PREF_ENTIRE_ROW_TOGGLES, true);
        put(PREF_LAST_STORE_FAILED, false);
        put(PREF_LEFT_HANDED, false);
        put(PREF_STAY_AWAKE, false);
        put(PREF_STRIKE_CHECKED, true);
    }};

    private final static Map<String, Integer> sIntDefaults = new HashMap<String, Integer>() {{
        put(PREF_TEXT_SIZE_INDEX, TEXT_SIZE_DEFAULT);
    }};

    private final static Map<String, String> sStringDefaults = new HashMap<String, String>() {{
        put(PREF_URI, null);
    }};

    private SharedPreferences mPrefs;

    public static final int REQUEST_IMPORT_LIST = 3;

    // Use for debug. bitmask, 0 = normal, 1 = fail network load, 2 = fail network and cache
    static final int FORCE_LOAD_FAIL = 0;

    private Checklists mLists; // List of lists
    private Thread mLoadThread = null;
    public boolean mListsLoaded = false;

    public Lister() {
        // Always need a checklists instance, as a place to attach listeners
        mLists = new Checklists();
    }

    public Checklists getLists() {
        return mLists;
    }

    public SharedPreferences getPrefs() {
        if (mPrefs == null)
            mPrefs = getSharedPreferences(null, Context.MODE_PRIVATE);
        return mPrefs;
    }

    /**
     * Load lists if (and only if) they haven't already been loaded and are residing in memory.
     * @param cxt ui we are loading from
     * @param onOK callback
     * @param onFail callback
     */
    public void loadLists(Context cxt, SuccessCallback onOK, FailCallback onFail) {

        if (mLoadThread != null && mLoadThread.isAlive()) {
            mLoadThread.interrupt();
            mLoadThread = null;
        }

        // Asynchronously load the URI. If the load fails, try the cache
        final Uri uri = getUri(PREF_URI);
        if (uri == null)
            return;
        mLoadThread = new Thread(() -> {
            Log.d(TAG, "Starting load thread to load from " + uri);
            try {
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
                if ((FORCE_LOAD_FAIL & 1) != 0)
                    throw new IOException("Load from net failure forced");
                else {
                    mLists.fromStream(stream);
                    mLists.setURI(uri.toString());
                    Log.d(TAG, mLists.size() + " lists loaded from " + uri);
                    // Check against the cache
                    loadCache(new Checklists(), cxt, o -> {
                        Checklists cachedLists = (Checklists)o;
                        Log.d(TAG, "Cache remembers URI " + cachedLists.getURI());
                        if (cachedLists.isMoreRecentVersionOf(mLists)) {
                            Log.d(TAG, "Cache is more recent");
                            loadCache(mLists, cxt, o2 -> {
                                mListsLoaded = true;
                                onOK.succeeded(mLists);
                            }, onFail);
                        } else {
                            mListsLoaded = true;
                            onOK.succeeded(mLists);
                        }
                    }, onFail);
                }
            } catch (final SecurityException se) {
                // openInputStream denied, pass back uri_acces_denied to reroute through picker
                // to re-establish permissions
                onFail.failed(R.string.uri_access_denied);
                Log.d(TAG, "Security Exception loading " + uri + ": " + se);
            } catch (Exception e) {
                Log.d(TAG, "Exception loading from " + uri + ": " + e);
                onFail.failed(R.string.failed_uri_load);
                loadCache(mLists, cxt, o -> {
                    Log.d(TAG, "Cache comes from " + mLists.getURI());
                    if (!mLists.getURI().equals(uri.toString()))
                        mLists.clear();
                    onOK.succeeded(mLists);
                }, onFail);
            }
        });
        mLoadThread.start();
    }

    private void loadCache(Checklists cacheLists, Context cxt, SuccessCallback onOK, FailCallback onFail) {
        try {
            if ((Lister.FORCE_LOAD_FAIL & 2) != 0)
                throw new Exception("Cache load fail forced");
            else {
                FileInputStream fis = cxt.openFileInput(CACHE_FILE);
                cacheLists.fromStream(fis);
                Log.d(TAG, cacheLists.size() + " lists loaded from cache");
                onOK.succeeded(cacheLists);
            }
        } catch (FileNotFoundException ce) {
            Log.d(TAG, "FileNotFoundException loading cache " + CACHE_FILE + ": " + ce);
            onFail.failed(R.string.no_cache);
        } catch (Exception ce) {
            Log.d(TAG, "Failed cache load " + CACHE_FILE + ": " + ce);
            onFail.failed(R.string.failed_cache_load);
        }
    }

    /**
     * Save changes.
     * If there's a failure saving to the backing store, we set lastStoreSaveFailed in the preferences.
     * If there's a failure saving to the cache, this is a major problem that may not be recoverable.
     */
    public void saveLists(Context cxt, SuccessCallback onSuccess, FailCallback onFail) {

        // Always save to the cache.
        Log.d(TAG, "Saving to cache");
        try {
            String jsonString = mLists.toJSON().toString(1);
            FileOutputStream stream = cxt.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE);
            stream.write(jsonString.getBytes());
            stream.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception saving to cache: " + e);
            onFail.failed(R.string.failed_save_to_cache);
        }

        final Uri uri = getUri(PREF_URI);
        if (uri == null)
            return;

        final byte[] data;
        try {
            String jsonString = mLists.toJSON().toString(1);

            // Launch a thread to do this save, so we don't block the ui thread
            Log.d(TAG, "Saving to " + uri);
            data = jsonString.getBytes();

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
                    setBool(PREF_LAST_STORE_FAILED, false);
                    onSuccess.succeeded(null);
                } catch (IOException ioe) {
                    Log.e(TAG, "Exception while saving to Uri " + ioe.getMessage());
                    setBool(PREF_LAST_STORE_FAILED, true);
                    onFail.failed(R.string.failed_save_to_uri);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "" + e);
            onFail.failed(R.string.failed_save_to_cache);
        }
    }

    public void importList(Uri uri, Context cxt, SuccessCallback onOK, FailCallback onFail) {
        if (uri == null) {
            Log.e(TAG, "Null URI");
            onFail.failed(R.string.failed_import);
            return;
        }
        try {
            InputStream stream;
            if (Objects.equals(uri.getScheme(), "file")) {
                stream = new FileInputStream(new File((uri.getPath())));
            } else if (Objects.equals(uri.getScheme(), "content")) {
                stream = cxt.getContentResolver().openInputStream(uri);
            } else {
                throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
            }
            Checklist newList = new Checklist(mLists, "Unknown");
            newList.fromStream(stream);

            mLists.add(newList);
            Log.d(TAG, "imported list: " + newList.getText());
            mLists.notifyChangeListeners();
            Log.d(TAG, "Save imported list");
            saveLists(cxt, d -> {}, onFail);
            onOK.succeeded(newList);
        } catch (Exception e) {
            Log.e(TAG, "import failed " + e.getMessage());
            onFail.failed(R.string.failed_import);
        }
    }

    public void handleChangeStore(Intent resultData, Context cxt) {
        Uri newURI = resultData.getData();
        if (newURI == null)
            return;

        Uri oldURI = getUri(PREF_URI);
        if (!newURI.equals(oldURI)) {
            setUri(PREF_URI, newURI);

            // Persist granted access across reboots
            int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cxt.getContentResolver().takePersistableUriPermission(newURI, takeFlags);
        }
    }

    public void notifyListsListeners() {
        mLists.notifyChangeListeners();
    }

    public int getInt(String name) {
        return getPrefs().getInt(name, sIntDefaults.get(name));
    }

    public void setInt(String name, int value) {
        SharedPreferences.Editor e = getPrefs().edit();
        sIntDefaults.put(name, value);
        e.putInt(name, value);
        e.apply();
    }

    public boolean getBool(String name) {
        return getPrefs().getBoolean(name, sBoolDefaults.get(name));
    }

    public void setBool(String name, boolean value) {
        SharedPreferences.Editor e = getPrefs().edit();
        e.putBoolean(name, value);
        e.apply();
    }

    public Uri getUri(String name) {
        String uris = getPrefs().getString(name, sStringDefaults.get(name));
        if (uris == null)
                return null;
        return Uri.parse(uris);
    }

    public void setUri(String name, Uri value) {
        SharedPreferences.Editor e = getPrefs().edit();
        e.putString(name, value.toString());
        e.apply();
    }

    public interface FailCallback {
        void failed(int resource);
    }

    public interface SuccessCallback {
        void succeeded(Object data);
    }
}
