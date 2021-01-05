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
import com.cdot.lists.model.Checklists
import org.json.JSONException
import java.io.*
import java.util.*

/**
 * Application singleton that handles data that is not specific to activities.
 */
class Lister : Application() {
    // Always need a checklists instance, as a place to attach listeners
    val lists = Checklists()

    private var listsAreLoaded = false
    var isLoading = false
    private var sharedPrefs: SharedPreferences? = null
    private var loadThread: Thread? = null

    val prefs: SharedPreferences
        get() {
            if (sharedPrefs == null) sharedPrefs = getSharedPreferences(null, Context.MODE_PRIVATE)
            return sharedPrefs!!
        }

    /**
     * Load lists if (and only if) they haven't already been loaded and are residing in memory.
     *
     * @param cxt    ui we are loading from
     * @param onOK   callback
     * @param onFail callback
     */
    fun loadLists(cxt: Context, onOK: SuccessCallback, onFail: FailCallback) {
        Log.d(TAG, "loadLists loaded=$listsAreLoaded loading=$isLoading")
        if (listsAreLoaded || isLoading) return
        isLoading = true

        // Asynchronously load the URI. If the load fails, try the cache
        loadThread = Thread {
            val thred = this
            val uri = getUri(PREF_FILE_URI)
            Log.d(TAG, "Starting load thread $thred to load from $uri")
            try {
                if (uri == null) throw Exception("Null URI (this is OK)")
                if (getBool(PREF_DEBUG) && getBool(PREF_DISABLE_FILE)) throw Exception("DEBUG File load disabled")
                val stream: InputStream =
                        when (uri.scheme) {
                            ContentResolver.SCHEME_FILE -> FileInputStream(File(uri.path!!)) // for tests
                            ContentResolver.SCHEME_CONTENT -> cxt.contentResolver.openInputStream(uri)!! // normal usage
                            else -> throw IOException("Failed to load lists. Unknown uri scheme: " + uri.scheme)
                        }
                lists.fromStream(stream)
                lists.forUri = uri.toString()
                // Check against the cache
                loadCache(Checklists(), cxt,
                        object : SuccessCallback {
                            override fun succeeded(data: Any?) {
                                val cachedLists = data as Checklists
                                Log.d(TAG, "Cache remembers URI " + cachedLists.forUri)
                                if (cachedLists.isMoreRecentVersionOf(lists)) {
                                    Log.d(TAG, "Cache is more recent")
                                    unloadLists()
                                    for (i in cachedLists.cloneChildren())
                                        lists.addChild(i)
                                }
                                listsAreLoaded = true
                                onOK.succeeded(lists)
                                Log.d(TAG, "Load thread " + thred + "finished")
                            }
                        },
                        object : FailCallback {
                            override fun failed(code: Int, vararg args: Any): Boolean {
                                onFail.failed(code, *args)
                                // Backing store loaded OK but cache failed. That's OK.
                                listsAreLoaded = true
                                onOK.succeeded(lists)
                                Log.d(TAG, "Load thread " + thred + "finished")
                                return true
                            }
                        })
            } catch (se: SecurityException) {
                // openInputStream denied, pass back uri_access_denied to reroute through picker
                // to re-establish permissions
                Log.e(TAG, "Security Exception " + stringifyException(se))
                onFail.failed(R.string.failed_access_denied)
                Log.d(TAG, "Load thread " + thred + "finished")
            } catch (e: Exception) {
                if (uri == null) {
                    onFail.failed(R.string.failed_no_file)
                } else {
                    Log.e(TAG, "Exception loading " + stringifyException(e))
                    onFail.failed(R.string.failed_file_load)
                }
                Log.d(TAG, "Loading $lists from cache")
                loadCache(lists, cxt,
                        object : SuccessCallback {
                            override fun succeeded(data: Any?) {
                                if (uri != null && lists.forUri != uri.toString()) lists.clear()
                                Log.d(TAG, lists.size().toString() + " lists loaded to " + lists + " from cache")
                                listsAreLoaded = true
                                onOK.succeeded(lists)
                                Log.d(TAG, "Load thread " + thred + "finished")
                            }
                        },
                        object : FailCallback {
                            override fun failed(code: Int, vararg args: Any): Boolean {
                                Log.d(TAG, "Load thread " + thred + "finished")
                                return onFail.failed(code, *args)
                            }
                        })
            }
            isLoading = false
            Log.d(TAG, "Load thread $thred finished loaded=$listsAreLoaded")
        }
        loadThread!!.start()
    }

    // Load from the cache file into a Checklists object
    private fun loadCache(cacheLists: Checklists, cxt: Context, onOK: SuccessCallback, onFail: FailCallback) {
        try {
            if (getBool(PREF_DEBUG) && getBool(PREF_DISABLE_CACHE)) throw Exception("DEBUG Cache load disabled")
            cacheLists.fromStream(cxt.openFileInput(CACHE_FILE))
            onOK.succeeded(cacheLists)
        } catch (ce: FileNotFoundException) {
            Log.e(TAG, "FileNotFoundException loading cache $CACHE_FILE: $ce")
            onFail.failed(R.string.snack_no_cache)
        } catch (ce: Exception) {
            Log.e(TAG, "Failed cache load " + CACHE_FILE + ": " + stringifyException(ce))
            onFail.failed(R.string.failed_cache_load)
        }
    }

    /**
     * Save a new cache of the lists currently loaded into the app
     */
    fun saveCache(cxt: Context): Boolean {
        try {
            if (getBool(PREF_DEBUG) && getBool(PREF_DISABLE_CACHE)) throw Exception("DEBUG Cache save disabled")
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
     * Save changes to the cache and backing store
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
        // Launch a thread to do this save, so we don't block the ui thread
        Thread {
            val data: ByteArray
            try {
                val jsonString = lists.toJSON().toString(1)
                data = jsonString.toByteArray()
            } catch (e: JSONException) {
                Log.e(TAG, "Should never happen " + stringifyException(e))
                onFail.failed(R.string.failed_save_to_uri)
                return@Thread
            }
            try {
                if (getBool(PREF_DEBUG) && getBool(PREF_DISABLE_FILE)) throw IOException("DEBUG File save disabled")
                val stream: OutputStream = when (uri.scheme) {
                    ContentResolver.SCHEME_FILE -> FileOutputStream(File(uri.path!!)) // for tests
                    ContentResolver.SCHEME_CONTENT -> cxt.contentResolver.openOutputStream(uri)
                    else -> throw IOException("Unknown uri scheme: " + uri.scheme)
                } ?: throw IOException("Stream open failed")
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
        }.start()
    }

    /**
     * Import lists from the given uri into the set of lists managed by the app
     */
    fun importList(uri: Uri, cxt: Context, onOK: SuccessCallback, onFail: FailCallback) {
        try {
            Log.d(TAG, "Importing from $uri")
            val stream: InputStream =
                    when (uri.scheme) {
                        ContentResolver.SCHEME_FILE -> FileInputStream(File(uri.path!!)) // for tests
                        ContentResolver.SCHEME_CONTENT -> cxt.contentResolver.openInputStream(uri)!! // normal usage
                        else -> throw IOException("Failed to load lists. Unknown uri scheme: " + uri.scheme)
                    }
            val importedLists = Checklists()
            importedLists.fromStream(stream)
            val ret = importedLists.cloneChildren()
            if (ret.isNotEmpty()) {
                var duplicates: List<String>? = null
                var added: List<String>? = null
                for (eli in ret) {
                    if (getBool(PREF_WARN_DUPLICATE) && lists.findByText(eli.text, false) != null)
                        duplicates = duplicates?.plus(eli.text) ?: arrayListOf(eli.text)
                    else {
                        added = added?.plus(eli.text) ?: arrayListOf(eli.text)
                        lists.addChild(eli) // depopulates importedLists, but not ret
                    }
                }
                if (duplicates != null)
                    onFail.failed(R.string.failed_duplicate_list, duplicates.joinToString())
                if (added != null)
                    saveLists(cxt,
                            object : SuccessCallback {
                                override fun succeeded(data: Any?) {
                                    Log.d(TAG, "Saved imported lists")
                                    onOK.succeeded(added)
                                }
                            }, onFail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "import failed " + stringifyException(e))
            onFail.failed(R.string.failed_import, e.toString())
        }
    }

    /**
     * Empty the object
     */
    fun unloadLists() {
        listsAreLoaded = false
        lists.clear()
    }

    /**
     * Get a value from Shared Preferences, applying the appropriate default
     */
    fun getInt(name: String): Int {
        val deflt = sDefaults[name] as Int?
        return prefs.getInt(name, deflt ?: 0)
    }

    /**
     * Set a value from Shared Preferences
     */
    fun setInt(name: String, value: Int) {
        val e = prefs.edit()
        e.putInt(name, value)
        e.apply()
    }

    /**
     * Get a value from Shared Preferences, applying the appropriate default
     */
    fun getBool(name: String): Boolean {
        val deflt = sDefaults[name] as Boolean?
        return prefs.getBoolean(name, deflt ?: false)
    }

    /**
     * Set a value from Shared Preferences
     */
    fun setBool(name: String, value: Boolean) {
        val e = prefs.edit()
        e.putBoolean(name, value)
        e.apply()
    }

    /**
     * Get a value from Shared Preferences, applying the appropriate default
     */
    fun getUri(name: String): Uri? {
        val deflt = sDefaults[name] as String?
        val uris = prefs.getString(name, deflt)
        return if (uris == null) null else Uri.parse(uris)
    }

    /**
     * Set a value in Shared Preferences
     */
    fun setUri(name: String, value: Uri?) {
        val e = prefs.edit()
        e.putString(name, value?.toString())
        e.apply()
    }

    /**
     * Callback used to report failures
     */
    interface FailCallback {
        /**
         * Return false if it's unsafe to continue with the operation after recording the failure.
         * The callback may be invoked several times during an operation, but the operation should
         * stop if failed() returns false.
         * @param code string resource (error) code
         * @param args to the resource template
         * @return true if the operation is safe to continue, false otherwise
         */
        fun failed(code: Int, vararg args: Any): Boolean
    }

    /**
     * Callback on success
     */
    interface SuccessCallback {
        /**
         * The contract is that this will be the last call in any successful operation
         * @param data is arbitrary and defined by the operation
         */
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
        const val PREF_DISABLE_FILE = "disableFile"
        const val PREF_DISABLE_CACHE = "disableCache"
        const val PREF_DEBUG = "debug"
        const val CACHE_FILE = "checklists.json"

        // Must match res/values/strings.xml/text_size_list
        const val TEXT_SIZE_DEFAULT = 0
        const val TEXT_SIZE_SMALL = 1
        const val TEXT_SIZE_MEDIUM = 2
        const val TEXT_SIZE_LARGE = 3

        private val TAG = Lister::class.simpleName

        // Integer preference defaults
        private val sDefaults: Map<String, Any?> = object : HashMap<String, Any?>() {
            init {
                put(PREF_TEXT_SIZE_INDEX, TEXT_SIZE_DEFAULT)
                put(PREF_FILE_URI, null)
                put(PREF_ALWAYS_SHOW, false)
                put(PREF_GREY_CHECKED, true)
                put(PREF_WARN_DUPLICATE, true)
                put(PREF_ENTIRE_ROW_TOGGLES, true)
                put(PREF_LAST_STORE_FAILED, false)
                put(PREF_LEFT_HANDED, false)
                put(PREF_STAY_AWAKE, false)
                put(PREF_STRIKE_CHECKED, true)
                put(PREF_DISABLE_FILE, false)
                put(PREF_DISABLE_CACHE, false)
                put(PREF_DEBUG, false)
            }
        }

        // Useful for debug
        @JvmStatic
        fun stringifyException(e: Exception): String {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }
}