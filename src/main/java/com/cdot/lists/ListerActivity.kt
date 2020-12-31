/*
 * Copyright © 2020 C-Dot Consultants
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.cdot.lists

import android.content.DialogInterface
import android.content.Intent
import android.content.Intent.EXTRA_MIME_TYPES
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cdot.lists.Lister.FailCallback
import com.cdot.lists.Lister.SuccessCallback
import com.cdot.lists.model.EntryListItem
import com.google.android.material.snackbar.Snackbar

abstract class ListerActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    /**
     * Get the application
     *
     * @return the main activity, parent of all fragments
     */
    val lister: Lister
        get() = application as Lister

    protected val mMessageHandler: Handler = Handler(Looper.getMainLooper()) {
        if (it.what == MESSAGE_UPDATE_DISPLAY) {
            invalidateOptionsMenu() // update menu items
            updateDisplay()
        }
        true
    }

    /**
     * Call to notify all change listeners that the list managed by this activity has changed in
     * some way, perhaps by reloading. Note this should NOT trigger a save, just a display update.
     */
    abstract fun updateDisplay()

    /**
     * Get the name of an HTML help asset appropriate for this activity
     * Override in subclasses that have preference screens
     * @return resource id for a raw html asset
     */
    open protected val helpAsset: Int = 0

    fun setShowOverLockScreen() {
        val show = lister.getBool(Lister.PREF_ALWAYS_SHOW)

        // None of this achieves anything on my Moto G8 Plus with Android 10, but works fine on a
        // G6 running Android 9.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            setShowWhenLocked(show)
        else {
            if (show)
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            else
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    fun setStayAwake() {
        val stay = lister.getBool(Lister.PREF_STAY_AWAKE)
        if (stay) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    public override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString(JSON_EXTRA, lister.lists.toJSON().toString())
    }

    public override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)
        lister.lists.fromJSON(state.getString(JSON_EXTRA)!!)
    }

    public override fun onPause() {
        lister.prefs?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    // Activity
    override fun onAttachedToWindow() {
        // onAttachedToWindow is called after onResume (and it happens only once per lifecycle).
        // ActivityThread.handleResumeActivity call will add DecorView to the current WindowManger
        // which will in turn call WindowManagerGlobal.addView() which than traverse all the views
        // and call onAttachedToWindow on each view.
        setShowOverLockScreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent " + intent.data)
    }

    // Activity
    public override fun onResume() {
        super.onResume()
        // Are we opening from a data source?
        if (Intent.ACTION_VIEW == intent.action && intent.data != null) {
            // If we are able to load from this URI, then the cache should be ignored
            if (lister.getUri(Lister.PREF_FILE_URI) != intent.data) {
                lister.setUri(Lister.PREF_FILE_URI, intent.data)
                lister.unloadLists()
                Log.d(TAG, "NEW URI from Intent " + lister.getUri(Lister.PREF_FILE_URI))
            } else
                Log.d(TAG, "SAME URI from Intent==Prefs " + lister.getUri(Lister.PREF_FILE_URI))
        } else
            Log.d(TAG, "OLD URI from Prefs " + lister.getUri(Lister.PREF_FILE_URI))
        ensureListsLoaded(
                null,
                object : FailCallback {
                    override fun failed(code: Int): Boolean {
                        return reportIndefinite(code)
                    }
                })
        setStayAwake() // re-acquire wakelock if necessary
        lister.prefs?.registerOnSharedPreferenceChangeListener(this)
    }

    /**
     * Called from onResume when the lists have been successfully loaded
     */
    protected open fun onListsLoaded() {}

    /**
     *
     * @param onOK actions on load success
     * @param onFail actions on load failed
     */
    @Synchronized
    fun ensureListsLoaded(onOK: SuccessCallback?, onFail: FailCallback) {
        val act = this;
        lister.loadLists(this,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        runOnUiThread { onListsLoaded() }
                        if (onOK != null) onOK.succeeded(data)
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int): Boolean {
                        if (code == R.string.failed_access_denied) {
                            // In a thread, have to use the UI thread to request access
                            runOnUiThread {
                                val builder = AlertDialog.Builder(act)
                                builder.setTitle(R.string.failed_access_denied)
                                builder.setMessage(R.string.failed_file_access)
                                builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    // Callers must include CATEGORY_OPENABLE in the Intent to obtain URIs that can be opened with ContentResolver#openFileDescriptor(Uri, String)
                                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                                    // Indicate the permissions should be persistable across device reboots
                                    intent.flags = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    if (Build.VERSION.SDK_INT >= 26) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, lister.getUri(Lister.PREF_FILE_URI))
                                    intent.type = "text/plain"
                                    intent.putExtra(EXTRA_MIME_TYPES, resources.getStringArray(R.array.share_format_mimetype))
                                    startActivityForResult(intent, REQUEST_CHANGE_STORE)
                                }
                                builder.show()
                            }
                            return true
                        }
                        return onFail.failed(code)
                    }
                });
    }

    /**
     * Checkpoint-save the lists
     */
    fun checkpoint() {
        lister.saveLists(this,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        Log.d(TAG, "checkpoint save OK")
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int): Boolean {
                        return reportShort(code)
                    }
                })
    }

    // Strictly speaking, this only applies in ChecklistsActivity, as it's the only place
    // these preferences can be changed
    // SharedPreferences.OnSharedPreferencesListener
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (Lister.PREF_ALWAYS_SHOW == key) setShowOverLockScreen() else if (Lister.PREF_STAY_AWAKE == key) setStayAwake()
    }

    protected abstract val rootView: View?

    open fun report(code: Int, duration: Int, vararg ps: Any): Boolean {
        runOnUiThread {
            if (ps.size > 0) {
                // Note use of spread (*) operator to pass varargs array as varargs
                Snackbar.make(rootView!!, resources.getString(code, *ps), duration)
                        .setAction(R.string.close) { x: View? -> }.show()
            } else {
                Snackbar.make(rootView!!, code, duration)
                        .setAction(R.string.close) { x: View? -> }.show()
            }
        }
        return true
    }
    fun reportShort(code: Int, vararg ps: Any) : Boolean { return report(code, Snackbar.LENGTH_SHORT, *ps) }
    fun reportLong(code: Int, vararg ps: Any) : Boolean { return report(code, Snackbar.LENGTH_SHORT, *ps) }
    fun reportIndefinite(code: Int, vararg ps: Any) : Boolean { return report(code, Snackbar.LENGTH_SHORT, *ps) }

    fun report(mess: String, duration: Int): Boolean {
        runOnUiThread {
            Snackbar.make(rootView!!, mess, duration)
                    .setAction(R.string.close) { x: View? -> }.show()
        }
        return true
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == REQUEST_PREFERENCES) {
            // Result of a PreferenceActivity invocation; preferences may have changed, we need
            // to redraw
            mMessageHandler.sendMessage(mMessageHandler.obtainMessage(MESSAGE_UPDATE_DISPLAY))
            return;
        }

        val lister = lister
        val act = this;
        val uri: Uri? = intent?.data
        if (requestCode == Lister.REQUEST_IMPORT && uri != null && resultCode == RESULT_OK) {
            lister.importList(uri, this,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            val report = StringBuilder()
                            for (i in data as List<*>) {
                                val eli : EntryListItem = i as EntryListItem;
                                Log.d(TAG, "Imported list: " + eli.text)
                                if (report.isNotEmpty()) report.append(", ")
                                report.append("'").append(eli.text).append("'")
                            }
                            reportIndefinite(R.string.snack_imported, report)
                            val msg = mMessageHandler.obtainMessage(MESSAGE_UPDATE_DISPLAY)
                            mMessageHandler.sendMessage(msg)
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int): Boolean {
                            return reportLong(code)
                        }
                    })
        } else if (requestCode == REQUEST_CHANGE_STORE && uri != null && resultCode == RESULT_OK) {
            if (uri != lister.getUri(Lister.PREF_FILE_URI)) {
                val flags = intent.flags
                lister.unloadLists()
                // Reload from the new store. Note that the listener for the Checklists activity
                // was removed when the Settings activity was invoked.
                lister.setUri(Lister.PREF_FILE_URI, uri)
                ensureListsLoaded(
                        object : SuccessCallback {
                            override fun succeeded(data: Any?) {
                                // Persist granted access across reboots
                                val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                lister.getUri(Lister.PREF_FILE_URI)?.let { contentResolver.takePersistableUriPermission(it, takeFlags) }
                                lister.saveCache(act)
                                val msg = mMessageHandler.obtainMessage(MESSAGE_UPDATE_DISPLAY)
                                mMessageHandler.sendMessage(msg)
                                reportLong(R.string.snack_file_changed)
                            }
                        },
                        object : FailCallback {
                            override fun failed(code: Int): Boolean {
                                return reportIndefinite(code)
                            }
                        })
            }
        } else if (requestCode == REQUEST_CREATE_STORE && intent != null && resultCode == RESULT_OK) {
            lister.setUri(Lister.PREF_FILE_URI, uri)
            // Save whatever is currently in memory to the new URI
            lister.saveLists(this,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            reportLong(R.string.snack_file_created)
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int): Boolean {
                            return reportShort(code)
                        }
                    });
        } else
            super.onActivityResult(requestCode, resultCode, intent)
    }

    companion object {
        // Request codes handled in onActivityResult
        const val REQUEST_CHANGE_STORE = 1
        const val REQUEST_CREATE_STORE = 2
        const val REQUEST_PREFERENCES = 3
        const val MESSAGE_UPDATE_DISPLAY = 0xC04EFE
        private val TAG = ListerActivity::class.simpleName
        private val CLASS_NAME = ListerActivity::class.java.canonicalName

        // Extras used for comms in intents between activities and saved instance states
        @JvmField
        val UID_EXTRA = "$CLASS_NAME.uid_extra"
        val JSON_EXTRA = "$CLASS_NAME.json_extra"
    }
}