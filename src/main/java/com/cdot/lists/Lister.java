/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.cdot.lists.model.Checklists;
import com.cdot.lists.model.EntryListItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Application singleton that handles the lists and preferences
 */
public class Lister extends Application {
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
    public static final String PREF_WARN_DUPLICATE = "warnDuplicates";
    public static final String CACHE_FILE = "checklists.json";
    // Must match res/values/strings.xml/text_size_list
    public static final int TEXT_SIZE_DEFAULT = 0;
    public static final int TEXT_SIZE_SMALL = 1;
    public static final int TEXT_SIZE_MEDIUM = 2;
    public static final int TEXT_SIZE_LARGE = 3;
    public static final int REQUEST_IMPORT = 3;
    private static final String TAG = Lister.class.getSimpleName();
    private final static Map<String, Boolean> sBoolDefaults = new HashMap<String, Boolean>() {{
        put(PREF_ALWAYS_SHOW, false);
        put(PREF_GREY_CHECKED, true);
        put(PREF_WARN_DUPLICATE, true);
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
    // TESTING ONLY
    public static int FORCE_CACHE_FAIL = 0;
    private final Checklists mLists; // List of lists
    private boolean mListsLoaded = false;
    private boolean mListsLoading = false;
    private SharedPreferences mPrefs;
    private Thread mLoadThread = null;

    public Lister() {
        // Always need a checklists instance, as a place to attach listeners
        mLists = new Checklists();
    }

    // Useful for debug
    public static String stringifyException(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
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
     *
     * @param cxt    ui we are loading from
     * @param onOK   callback
     * @param onFail callback
     */
    public void loadLists(Context cxt, SuccessCallback onOK, FailCallback onFail) {

        // We can arrive here after synchronization lock is released by a previous load
        if (mListsLoaded || mListsLoading)
            return;

        mListsLoading = true;

        // Asynchronously load the URI. If the load fails, try the cache
        mLoadThread = new Thread(() -> {
            final Uri uri = getUri(PREF_URI);

            Log.d(TAG, "Starting load thread to load from " + uri);
            try {
                if (uri == null)
                    throw new Exception("Null URI (this is OK)");
                InputStream stream;
                if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                    stream = new FileInputStream(new File((uri.getPath())));
                } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                    stream = cxt.getContentResolver().openInputStream(uri);
                } else {
                    throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                }

                mLists.fromStream(stream);
                mLists.setURI(uri.toString());
                // Check against the cache
                loadCache(new Checklists(), cxt,
                        o -> {
                            Checklists cachedLists = (Checklists) o;
                            Log.d(TAG, "Cache remembers URI " + cachedLists.getURI());
                            if (cachedLists.isMoreRecentVersionOf(mLists)) {
                                Log.d(TAG, "Cache is more recent");
                                loadCache(mLists, cxt,
                                        o2 -> {
                                            Log.d(TAG, mLists.size() + " lists loaded from cache");
                                            mListsLoading = false;
                                            mListsLoaded = true;
                                            onOK.succeeded(mLists);
                                        },
                                        code -> {
                                            // Could not load from cache, even though we already loaded from cache!
                                            mListsLoading = false;
                                            return onFail.failed(code);
                                        });
                            } else {
                                mListsLoaded = true;
                                mListsLoading = false;
                                onOK.succeeded(mLists);
                            }
                        },
                        code -> {
                            onFail.failed(code);
                            mListsLoading = false;
                            // Backing store loaded OK but cache failed. That's OK.
                            mListsLoaded = true;
                            onOK.succeeded(mLists);
                            return true;
                        });
            } catch (final SecurityException se) {
                // openInputStream denied, pass back uri_acces_denied to reroute through picker
                // to re-establish permissions
                Log.e(TAG, "Security Exception " + stringifyException(se));
                mListsLoading = false;
                onFail.failed(R.string.failed_access_denied);
            } catch (Exception e) {
                if (uri == null) {
                    onFail.failed(R.string.failed_no_uri);
                } else {
                    Log.e(TAG, "Exception loading " + Lister.stringifyException(e));
                    onFail.failed(R.string.failed_uri_load);
                }
                Log.d(TAG, "Loading " + mLists + " from cache");
                loadCache(mLists, cxt,
                        o -> {
                            if (uri != null && !mLists.getURI().equals(uri.toString()))
                                mLists.clear();
                            Log.d(TAG, mLists.size() + " lists loaded to " + mLists + " from cache");
                            mListsLoaded = true;
                            mListsLoading = false;
                            onOK.succeeded(mLists);
                        },
                        code -> {
                            mListsLoading = false;
                            return onFail.failed(code);
                        });
            }
        });
        mLoadThread.start();
    }

    private void loadCache(Checklists cacheLists, Context cxt, SuccessCallback onOK, FailCallback onFail) {
        try {
            if (FORCE_CACHE_FAIL != 0) {
                // TESTING ONLY
                onFail.failed(FORCE_CACHE_FAIL);
            } else {
                FileInputStream fis = cxt.openFileInput(CACHE_FILE);
                cacheLists.fromStream(fis);
                onOK.succeeded(cacheLists);
            }
        } catch (FileNotFoundException ce) {
            Log.e(TAG, "FileNotFoundException loading cache " + CACHE_FILE + ": " + ce);
            onFail.failed(R.string.no_cache);
        } catch (Exception ce) {
            Log.e(TAG, "Failed cache load " + CACHE_FILE + ": " + stringifyException(ce));
            onFail.failed(R.string.failed_cache_load);
        }
    }

    boolean saveCache(Context cxt) {
        try {
            if (FORCE_CACHE_FAIL != 0) // unit testing only
                throw new Exception("TEST CACHE SAVE FAIL");
            String jsonString = mLists.toJSON().toString(1);
            FileOutputStream stream = cxt.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE);
            stream.write(jsonString.getBytes());
            stream.close();
            Log.d(TAG, "Saved " + mLists.size() + " lists to cache");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Exception saving to cache: " + stringifyException(e));
        }
        return false;
    }

    /**
     * Save changes.
     * If there's a failure saving to the backing store, we set lastStoreSaveFailed in the preferences.
     * If there's a failure saving to the cache, this is a major problem that may not be recoverable.
     *
     * @param onSuccess succeeded will be called with null parameter
     * @param onFail    failed will be called with a resource id indicating the type of failure
     */
    public void saveLists(Context cxt, SuccessCallback onSuccess, FailCallback onFail) {

        // Always save to the cache.
        final boolean cacheOK = saveCache(cxt);
        if (!cacheOK)
            onFail.failed(R.string.failed_save_to_cache);

        final Uri uri = getUri(PREF_URI);
        if (uri == null) {
            if (cacheOK)
                onSuccess.succeeded(null);
            else
                onFail.failed(R.string.failed_save_to_cache_and_uri);
            return;
        }

        final byte[] data;
        try {
            String jsonString = mLists.toJSON().toString(1);

            // Launch a thread to do this save, so we don't block the ui thread
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
                    setBool(PREF_LAST_STORE_FAILED, false);
                    Log.d(TAG, "Saved " + mLists.size() + " lists to " + uri);
                    onSuccess.succeeded(null);
                } catch (IOException ioe) {
                    Log.e(TAG, "Exception while saving " + stringifyException(ioe));
                    setBool(PREF_LAST_STORE_FAILED, true);
                    onFail.failed(R.string.failed_save_to_uri);
                    if (cacheOK)
                        onSuccess.succeeded(null);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, stringifyException(e));
            onFail.failed(R.string.failed_save_to_uri);
        }
    }

    public void importList(Uri uri, Context cxt, SuccessCallback onOK, FailCallback onFail) {
        if (uri == null) {
            Log.e(TAG, "Null URI");
            onFail.failed(R.string.failed_import);
            return;
        }
        try {
            Log.d(TAG, "Importing from " + uri);
            InputStream stream;
            // Work out the mime type
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if ("json".equals(extension))
                type = "application/json";
            else if ("csv".equals(extension))
                type = "text.csv";
            else if (extension != null)
                // getting desperate
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (type == null)
                // last ditch
                type = cxt.getContentResolver().getType(uri);

            if (Objects.equals(uri.getScheme(), "file"))
                stream = new FileInputStream(new File(uri.getPath()));
            else if (Objects.equals(uri.getScheme(), "content"))
                stream = cxt.getContentResolver().openInputStream(uri);
            else
                throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());

            Checklists newLists = new Checklists();
            newLists.fromStream(stream, type);
            List<EntryListItem> ret = newLists.cloneItemList();
            for (EntryListItem eli : ret)
                mLists.addChild(eli); // depopulates newLists, but not ret
            Log.d(TAG, "Imported " + ret.size() + " lists, now have " + mLists.size() + " lists, saving");
            saveLists(cxt, d -> {
                Log.d(TAG, "Saved imported lists");
                onOK.succeeded(ret);
            }, onFail);
        } catch (Exception e) {
            Log.e(TAG, "import failed " + Lister.stringifyException(e));
            onFail.failed(R.string.failed_import);
        }
    }

    void unloadLists() {
        mListsLoaded = false;
        mLists.getData().clear();
   }

    // Shared Preferences

    public int getInt(String name) {
        Integer deflt = sIntDefaults.get(name);
        return getPrefs().getInt(name, deflt == null ? 0 : deflt);
    }

    public void setInt(String name, int value) {
        SharedPreferences.Editor e = getPrefs().edit();
        e.putInt(name, value);
        e.apply();
    }

    public boolean getBool(String name) {
        Boolean deflt = sBoolDefaults.get(name);
        return getPrefs().getBoolean(name, deflt == null ? false : deflt);
    }

    public void setBool(String name, boolean value) {
        SharedPreferences.Editor e = getPrefs().edit();
        e.putBoolean(name, value);
        e.apply();
    }

    public Uri getUri(String name) {
        String deflt = sStringDefaults.get(name);
        String uris = getPrefs().getString(name, deflt);
        return (uris == null) ? null : Uri.parse(uris);
    }

    public void setUri(String name, Uri value) {
        SharedPreferences.Editor e = getPrefs().edit();
        e.putString(name, value == null ? null : value.toString());
        e.apply();
    }

    public interface FailCallback {
        /**
         * Return false if it's unsafe to continue with the operation after recording the failure
         *
         * @param resource resource (error) code
         * @return true if the operation is safe to continue, false otherwise
         */
        boolean failed(int resource);
    }

    public interface SuccessCallback {
        void succeeded(Object data);
    }
}
