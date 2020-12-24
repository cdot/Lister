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
package com.cdot.lists;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ListerActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    // Request codes handled in onActivityResult
    public static final int REQUEST_CHANGE_STORE = 1;
    public static final int REQUEST_CREATE_STORE = 2;
    public static final int REQUEST_PREFERENCES = 3;
    private static final String TAG = ListerActivity.class.getSimpleName();
    private static final String CLASS_NAME = ListerActivity.class.getCanonicalName();
    // Extras used for comms in intents between activities and saved instance states
    public static final String UID_EXTRA = CLASS_NAME + ".uid_extra";
    public static final String JSON_EXTRA = CLASS_NAME + ".json_extra";

    /**
     * Get the application
     *
     * @return the main activity, parent of all fragments
     */
    public Lister getLister() {
        return (Lister) getApplication();
    }

    void setShowOverLockScreen() {
        boolean show = getLister().getBool(Lister.PREF_ALWAYS_SHOW);

        // None of this achieves anything on my Moto G8 Plus with Android 10, but works fine on a
        // G6 running Android 9.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            setShowWhenLocked(show);
        else {
            if (show)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            else
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    void setStayAwake() {
        boolean stay = getLister().getBool(Lister.PREF_STAY_AWAKE);
        Log.d(TAG, "Settings: stay awake " + stay);
        if (stay)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(JSON_EXTRA, getLister().getLists().toJSON().toString());
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle state) {
        super.onRestoreInstanceState(state);
        getLister().getLists().fromJSON(state.getString(JSON_EXTRA));
    }

    @Override
    public void onPause() {
        getLister().getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override // Activity
    public void onAttachedToWindow() {
        // onAttachedToWindow is called after onResume (and it happens only once per lifecycle).
        // ActivityThread.handleResumeActivity call will add DecorView to the current WindowManger
        // which will in turn call WindowManagerGlobal.addView() which than traverse all the views
        // and call onAttachedToWindow on each view.
        setShowOverLockScreen();
    }

    @Override // Activity
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        ensureListsLoaded();
        setStayAwake(); // re-acquire wakelock if necessary
        getLister().getPrefs().registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Called from onCreate and onResume when the lists have been successfully loaded
     */
    protected void onListsLoaded() {
    }

    // Ensure lists are always loaded
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ensureListsLoaded();
    }

    private synchronized void ensureListsLoaded() {
        if (getLister().mListsLoaded)
            return;
        getLister().loadLists(this,
                lists -> runOnUiThread(this::onListsLoaded),
                code -> {
                    if (code == R.string.uri_access_denied) {
                        // In a thread, have to use the UI thread to request access
                        runOnUiThread(() -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setTitle(R.string.uri_access_denied);
                            builder.setMessage(R.string.failed_uri_access);
                            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                // Callers must include CATEGORY_OPENABLE in the Intent to obtain URIs that can be opened with ContentResolver#openFileDescriptor(Uri, String)
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                // Indicate the permissions should be persistable across device reboots
                                intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                if (Build.VERSION.SDK_INT >= 26)
                                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getLister().getUri(Lister.PREF_URI));
                                intent.setType("application/json");
                                startActivityForResult(intent, REQUEST_CHANGE_STORE);
                                // onActivityResult will re-load the list in response to a successful
                                // REQUEST_CHANGE_STORE
                            });
                            builder.show();
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, code, Toast.LENGTH_LONG).show());
                    }
                });
    }

    /**
     * Checkpoint-save the lists
     */
    public void checkpoint() {
        getLister().saveLists(this,
                okdata -> Log.d(TAG, "checkpoint save OK"),
                code -> runOnUiThread(() ->
                        Toast.makeText(this, code, Toast.LENGTH_SHORT).show()));
    }

    // Strictly speaking, this only applies in ChecklistsActivity, as it's the only place
    // these preferences can be changed
    @Override // SharedPreferences.OnSharedPreferencesListener
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getLister().PREF_ALWAYS_SHOW.equals(key))
            setShowOverLockScreen();
        else if (getLister().PREF_STAY_AWAKE.equals(key))
            setStayAwake();
    }
}
