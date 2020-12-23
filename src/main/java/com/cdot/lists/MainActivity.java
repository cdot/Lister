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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.cdot.lists.databinding.MainActivityBinding;
import com.cdot.lists.fragment.ChecklistFragment;
import com.cdot.lists.fragment.ChecklistsFragment;
import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final int REQUEST_CHANGE_STORE = 1;

    public Lister getLister() {
        return (Lister)getApplication();
    }
    
    /**
     * Get the fragment at the top of the back stack
     *
     * @return a fragment
     */
    public EntryListFragment getEntryListFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment f = fm.findFragmentById(R.id.fragment);
        if (f instanceof EntryListFragment)
            return (EntryListFragment) f;
        return null;
    }
    
    @Override // FragmentActivity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainActivityBinding binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getLister().getPrefs().registerOnSharedPreferenceChangeListener((this));

        // Lists will be loaded in onResume
        Fragment f = new ChecklistsFragment(getLister().getLists());

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.fragment, f, TAG).commit();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("lists", getLister().getLists().toJSON().toString());
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle state) {
        super.onRestoreInstanceState(state);
        getLister().getLists().fromJSON(state.getString("lists"));
    }

    private void setShowOverLockScreen() {
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

    private void setStayAwake() {
        boolean stay = getLister().getBool(Lister.PREF_STAY_AWAKE);
        Log.d(TAG, "Settings: stay awake " + stay);
        if (stay)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override // FragmentActivity
    public void onAttachedToWindow() {
        // onAttachedToWindow is called after onResume (and it happens only once per lifecycle).
        // ActivityThread.handleResumeActivity call will add DecorView to the current WindowManger
        // which will in turn call WindowManagerGlobal.addView() which than traverse all the views
        // and call onAttachedToWindow on each view.
        setShowOverLockScreen();
    }

    @Override // FragmentActivity
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        // TODO: It would be sensible to lock the screen immediately if stayAwake is enabled
    }

    @Override // FragmentActivity
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        getLister().loadLists(this,
                data -> runOnUiThread(() -> ((Checklists)data).notifyChangeListeners()),
                code -> {
                    if (code == R.string.uri_access_denied) {
                        // In a thread, have to use the UI thread to request access
                        runOnUiThread(() -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle(R.string.uri_access_denied);
                            builder.setMessage(R.string.failed_uri_access);
                            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                // Callers must include CATEGORY_OPENABLE in the Intent to obtain URIs that can be opened with ContentResolver#openFileDescriptor(Uri, String)
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                // Indicate the permissions should be persistable across device reboots
                                intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                //if (Build.VERSION.SDK_INT >= 26)
                                //    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                                intent.setType("application/json");
                                startActivityForResult(intent, REQUEST_CHANGE_STORE);
                                // onActivityResult will re-load the list in response to a successful
                                // REQUEST_CHANGE_STORE
                            });
                            builder.show();
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, code, Toast.LENGTH_LONG).show());
                    }
                });
        setStayAwake(); // re-acquire wakelock if necessary
    }

    @Override // FragmentActivity
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        // dispatch to current fragment
        EntryListFragment elf = getEntryListFragment();
        if (elf != null && elf.dispatchTouchEvent(motionEvent))
            return true;
        return super.dispatchTouchEvent(motionEvent);
    }

    @Override // FragmentActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        if (resultCode != Activity.RESULT_OK || resultData == null)
            return;

        if (requestCode == Lister.REQUEST_IMPORT_LIST) {
            getLister().importList(resultData.getData(), this,
                    data -> {
                        Checklist newList = (Checklist) data;
                        invalidateOptionsMenu(); // update menu items
                        Toast.makeText(MainActivity.this, getString(R.string.import_report, newList.getText()), Toast.LENGTH_LONG).show();
                        pushFragment(new ChecklistFragment(newList));
                    },
                    resource -> Toast.makeText(MainActivity.this, resource, Toast.LENGTH_LONG).show());
        } else if (requestCode == REQUEST_CHANGE_STORE || requestCode == Lister.REQUEST_CREATE_STORE)
            getLister().handleChangeStore(resultData, this);
    }

    /**
     * Hide the current fragment, pushing it onto the stack, then open a new fragment.
     *
     * @param fragment the fragment to switch to
     */
    public void pushFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ftx = fm.beginTransaction();
        // Hide, but don't close, the open fragment (which will always be the lists view)
        ftx.hide(fm.findFragmentById(R.id.fragment));
        ftx.add(R.id.fragment, fragment, fragment.getClass().getName());
        ftx.addToBackStack(null);
        ftx.commit();
    }

    @Override // AppCompatActivity
    public void onBackPressed() {
        super.onBackPressed();
        // see https://medium.com/@Wingnut/onbackpressed-for-fragments-357b2bf1ce8e for info
        // on passing onBackPressed to fragments
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof EntryListFragment)
            // when going back, onResume is not called in the target
            ((EntryListFragment) frag).onActivated();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getLister().PREF_ALWAYS_SHOW.equals(key))
            setShowOverLockScreen();
        else if (getLister().PREF_STAY_AWAKE.equals(key))
            setStayAwake();
    }
}