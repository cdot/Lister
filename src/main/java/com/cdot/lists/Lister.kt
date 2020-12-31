/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.cdot.lists.model.Checklists
import java.io.*
import java.util.*

/**
 * Application singleton that handles the lists and preferences
 */
class Lister : Application() {
    val lists: Checklists
    private var mListsLoaded = false
    private var mListsLoading = false
    private var mPrefs: SharedPreferences? = null
    private var mLoadThread: Thread? = null

    val prefs: SharedPreferences?
        get() {
            if (mPrefs == null) mPrefs = getSharedPreferences(null, Context.MODE_PRIVATE)
            return mPrefs
        }

    /**
     * Load lists if (and only if) they haven't already been loaded and are residing in memory.
     *
     * @param cxt    ui we are loading from
     * @param onOK   callback
     * @param onFail callback
     */
    fun loadLists(cxt: Context, onOK: SuccessCallback, onFail: FailCallback) {

        // We can arrive here after synchronization lock is released by a previous load
        if (mListsLoaded || mListsLoading) return
        mListsLoading = true

        // Asynchronously load the URI. If the load fails, try the cache
        mLoadThread = Thread(Runnable {
            val uri = getUri(PREF_FILE_URI)
            Log.d(TAG, "Starting load thread to load from $uri")
            try {
                if (uri == null) throw Exception("Null URI (this is OK)")
                val stream: InputStream?
                stream = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                    FileInputStream(File(uri.path!!))
                } else if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    cxt.contentResolver.openInputStream(uri)
                } else {
                    throw IOException("Failed to load lists. Unknown uri scheme: " + uri.scheme)
                }
                lists.fromStream(stream)
                lists.uRI = uri.toString()
                // Check against the cache
                loadCache(Checklists(), cxt,
                        object : SuccessCallback {
                            override fun succeeded(data: Any?) {
                                val cachedLists = data as Checklists
                                Log.d(TAG, "Cache remembers URI " + cachedLists.uRI)
                                if (cachedLists.isMoreRecentVersionOf(lists)) {
                                    Log.d(TAG, "Cache is more recent")
                                    loadCache(lists, cxt,
                                            object : SuccessCallback {
                                                override fun succeeded(data: Any?) {
                                                    Log.d(TAG, lists.size().toString() + " lists loaded from cache")
                                                    mListsLoading = false
                                                    mListsLoaded = true
                                                    onOK.succeeded(lists)
                                                }
                                            },
                                            object : FailCallback {
                                                override fun failed(code: Int): Boolean {
                                                    // Could not load from cache, even though we already loaded from cache!
                                                    mListsLoading = false
                                                    return onFail.failed(code)
                                                }
                                            })
                                } else {
                                    mListsLoaded = true
                                    mListsLoading = false
                                    onOK.succeeded(lists)
                                }
                            }
                        },
                        object : FailCallback {
                            override fun failed(code: Int): Boolean {
                                onFail.failed(code)
                                mListsLoading = false
                                // Backing store loaded OK but cache failed. That's OK.
                                mListsLoaded = true
                                onOK.succeeded(lists)
                                return true
                            }
                        })
            } catch (se: SecurityException) {
                // openInputStream denied, pass back uri_access_denied to reroute through picker
                // to re-establish permissions
                Log.e(TAG, "Security Exception " + stringifyException(se))
                mListsLoading = false
                onFail.failed(R.string.failed_access_denied)
            } catch (e: Exception) {
                if (uri == null) {
                    onFail.failed(R.string.failed_no_file)
                } else {
                    Log.e(TAG, "Exception loading " + stringifyException(e))
                    onFail.failed(R.string.failed_file_load)
                }
                Log.d(TAG, "Loading " + lists + " from cache")
                loadCache(lists, cxt,
                        object : SuccessCallback {
                            override fun succeeded(data: Any?) {
                                if (uri != null && lists.uRI != uri.toString()) lists.clear()
                                Log.d(TAG, lists.size().toString() + " lists loaded to " + lists + " from cache")
                                mListsLoaded = true
                                mListsLoading = false
                                onOK.succeeded(lists)
                            }
                        },
                        object : FailCallback {
                            override fun failed(code: Int): Boolean {
                                mListsLoading = false
                                return onFail.failed(code)
                            }
                        })
            }
        })
        mLoadThread!!.start()
    }

    private fun loadCache(cacheLists: Checklists, cxt: Context, onOK: SuccessCallback, onFail: FailCallback) {
        try {
            if (FORCE_CACHE_FAIL != 0) {
                // TESTING ONLY
                onFail.failed(FORCE_CACHE_FAIL)
            } else {
                val fis = cxt.openFileInput(CACHE_FILE)
                cacheLists.fromStream(fis)
                onOK.succeeded(cacheLists)
            }
        } catch (ce: FileNotFoundException) {
            Log.e(TAG, "FileNotFoundException loading cache $CACHE_FILE: $ce")
            onFail.failed(R.string.snack_no_cache)
        } catch (ce: Exception) {
            Log.e(TAG, "Failed cache load " + CACHE_FILE + ": " + stringifyException(ce))
            onFail.failed(R.string.failed_cache_load)
        }
    }

    fun saveCache(cxt: Context): Boolean {
        try {
            if (FORCE_CACHE_FAIL != 0) throw Exception("TEST CACHE SAVE FAIL")
            val jsonString = lists.toJSON().toString(1)
            val stream = cxt.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE)
            stream.write(jsonString.toByteArray())
            stream.close()
            Log.d(TAG, "Saved " + lists.size() + " lists to cache")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving to cache: " + stringifyException(e))
        }
        return false
    }

    /**
     * Save changes.
     * If there's a failure saving to the backing store, we set lastStoreSaveFailed in the preferences.
     * If there's a failure saving to the cache, this is a major problem that may not be recoverable.
     *
     * @param onSuccess succeeded will be called with null parameter
     * @param onFail    failed will be called with a resource id indicating the type of failure
     */
    fun saveLists(cxt: Context, onSuccess: SuccessCallback, onFail: FailCallback) {

        // Always save to the cache.
        val cacheOK = saveCache(cxt)
        if (!cacheOK) onFail.failed(R.string.failed_save_to_cache)
        val uri = getUri(PREF_FILE_URI)
        if (uri == null) {
            if (cacheOK) onSuccess.succeeded(null) else onFail.failed(R.string.failed_save_to_cache_and_file)
            return
        }
        val data: ByteArray
        try {
            val jsonString = lists.toJSON().toString(1)

            // Launch a thread to do this save, so we don't block the ui thread
            data = jsonString.toByteArray()
            Thread(Runnable {
                val stream: OutputStream?
                try {
                    val scheme = uri.scheme
                    stream = if (scheme == ContentResolver.SCHEME_FILE) {
                        val path = uri.path
                        FileOutputStream(File(path!!))
                    } else if (scheme == ContentResolver.SCHEME_CONTENT) cxt.contentResolver.openOutputStream(uri) else throw IOException("Unknown uri scheme: " + uri.scheme)
                    if (stream == null) throw IOException("Stream open failed")
                    stream.write(data)
                    stream.close()
                    setBool(PREF_LAST_STORE_FAILED, false)
                    Log.d(TAG, "Saved " + lists.size() + " lists to " + uri)
                    onSuccess.succeeded(null)
                } catch (ioe: IOException) {
                    Log.e(TAG, "Exception while saving " + stringifyException(ioe))
                    setBool(PREF_LAST_STORE_FAILED, true)
                    onFail.failed(R.string.failed_save_to_uri)
                    if (cacheOK) onSuccess.succeeded(null)
                }
            }).start()
        } catch (e: Exception) {
            Log.e(TAG, stringifyException(e))
            onFail.failed(R.string.failed_save_to_uri)
        }
    }

    fun importList(uri: Uri, cxt: Context, onOK: SuccessCallback, onFail: FailCallback) {
        try {
            Log.d(TAG, "Importing from $uri")
            val stream: InputStream?
            // Work out the mime type
            var type: String? = null
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if ("json" == extension) type = "application/json" else if ("csv" == extension) type = "text.csv" else if (extension != null) // getting desperate
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (type == null) // last ditch
                type = cxt.contentResolver.getType(uri)
            stream = if (uri.scheme == "file") FileInputStream(File(uri.path!!)) else if (uri.scheme == "content") cxt.contentResolver.openInputStream(uri) else throw IOException("Failed to load lists. Unknown uri scheme: " + uri.scheme)
            val newLists = Checklists()
            newLists.fromStream(stream, type!!)
            val ret = newLists.cloneItemList()
            for (eli in ret) lists.addChild(eli) // depopulates newLists, but not ret
            Log.d(TAG, "Imported " + ret.size + " lists, now have " + lists.size() + " lists, saving")
            saveLists(cxt,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            Log.d(TAG, "Saved imported lists")
                            onOK.succeeded(ret)
                        }
                    }, onFail)
        } catch (e: Exception) {
            Log.e(TAG, "import failed " + stringifyException(e))
            onFail.failed(R.string.failed_import)
        }
    }

    fun unloadLists() {
        mListsLoaded = false
        lists.clear()
    }

    // Shared Preferences
    fun getInt(name: String?): Int {
        val deflt = sIntDefaults[name]
        return prefs!!.getInt(name, deflt ?: 0)
    }

    fun setInt(name: String?, value: Int) {
        val e = prefs!!.edit()
        e.putInt(name, value)
        e.apply()
    }

    fun getBool(name: String?): Boolean {
        val deflt = sBoolDefaults[name]
        return prefs!!.getBoolean(name, deflt ?: false)
    }

    fun setBool(name: String?, value: Boolean) {
        val e = prefs!!.edit()
        e.putBoolean(name, value)
        e.apply()
    }

    fun getUri(name: String?): Uri? {
        val deflt = sStringDefaults[name]
        val uris = prefs!!.getString(name, deflt)
        return if (uris == null) null else Uri.parse(uris)
    }

    fun setUri(name: String?, value: Uri?) {
        val e = prefs!!.edit()
        e.putString(name, value?.toString())
        e.apply()
    }

    interface FailCallback {
        /**
         * Return false if it's unsafe to continue with the operation after recording the failure
         *
         * @param code resource (error) code
         * @return true if the operation is safe to continue, false otherwise
         */
        fun failed(code: Int): Boolean
    }

    interface SuccessCallback {
        fun succeeded(data: Any?)
    }

    companion object {
        // Shared Preferences
        const val PREF_ALWAYS_SHOW = "showListInFrontOfLockScreen"
        const val PREF_GREY_CHECKED = "greyCheckedItems"
        const val PREF_ENTIRE_ROW_TOGGLES = "entireRowTogglesItem"
        const val PREF_LAST_STORE_FAILED = "lastStoreSaveFailed"
        const val PREF_LEFT_HANDED = "checkBoxOnLeftSide"
        const val PREF_STAY_AWAKE = "stayAwake"
        const val PREF_STRIKE_CHECKED = "strikeThroughCheckedItems"
        const val PREF_TEXT_SIZE_INDEX = "textSizeIndex"
        const val PREF_FILE_URI = "backingStore"
        const val PREF_WARN_DUPLICATE = "warnDuplicates"
        const val CACHE_FILE = "checklists.json"

        // Must match res/values/strings.xml/text_size_list
        const val TEXT_SIZE_DEFAULT = 0
        const val TEXT_SIZE_SMALL = 1
        const val TEXT_SIZE_MEDIUM = 2
        const val TEXT_SIZE_LARGE = 3
        const val REQUEST_IMPORT = 3
        private val TAG = Lister::class.java.simpleName
        private val sBoolDefaults: Map<String, Boolean> = object : HashMap<String, Boolean>() {
            init {
                put(PREF_ALWAYS_SHOW, false)
                put(PREF_GREY_CHECKED, true)
                put(PREF_WARN_DUPLICATE, true)
                put(PREF_ENTIRE_ROW_TOGGLES, true)
                put(PREF_LAST_STORE_FAILED, false)
                put(PREF_LEFT_HANDED, false)
                put(PREF_STAY_AWAKE, false)
                put(PREF_STRIKE_CHECKED, true)
            }
        }
        private val sIntDefaults: Map<String, Int> = object : HashMap<String, Int>() {
            init {
                put(PREF_TEXT_SIZE_INDEX, TEXT_SIZE_DEFAULT)
            }
        }
        private val sStringDefaults: Map<String, String?> = object : HashMap<String, String?>() {
            init {
                put(PREF_FILE_URI, null)
            }
        }

        // TESTING ONLY
        @JvmField
        var FORCE_CACHE_FAIL = 0

        // Useful for debug
        @JvmStatic
        fun stringifyException(e: Exception): String {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }

    init {
        // Always need a checklists instance, as a place to attach listeners
        lists = Checklists()
    }
}