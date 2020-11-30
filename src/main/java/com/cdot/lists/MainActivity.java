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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.cdot.lists.databinding.MainActivityBinding;
import com.cdot.lists.fragment.ChecklistFragment;
import com.cdot.lists.fragment.ChecklistsFragment;
import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    // AppCompatActivity is a subclass of androidx.fragment.app.FragmentActivity
    // It adds support for fragment options menus (and probably a lot more)
    private static final String TAG = "MainActivity";

    public static final int REQUEST_CHANGE_STORE = 1;
    public static final int REQUEST_CREATE_STORE = 2;
    public static final int REQUEST_IMPORT_LIST = 3;

    // Use for debug. bitmask, 0 = normal, 1 = fail network load, 2 = fail network and cache
    private static int FORCE_LOAD_FAIL = 0;

    private Checklists mLists; // List of lists

    /**
     * Get the fragment at the top of the back stack
     *
     * @return a fragment
     */
    public EntryListFragment getEntryListFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment f = fm.findFragmentById(R.id.fragment);
        if (f instanceof EntryListFragment)
            return (EntryListFragment)f;
        return null;
    }

    @Override // FragmentActivity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainActivityBinding binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mLists = new Checklists();
        Settings.setContext(this);

        // Lists will be loaded in onResume
        Fragment f = new ChecklistsFragment(mLists);

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.fragment, f, TAG).commit();
    }

    @Override // FragmentActivity
    public void onAttachedToWindow() {
        if (Settings.getBool(Settings.alwaysShow)) {
            getWindow().addFlags(AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
        }
    }

    @Override // FragmentActivity
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - loading lists");
        loadLists();
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

        if (requestCode == REQUEST_IMPORT_LIST)
            handleImport(resultData.getData());
        else if (requestCode == REQUEST_CHANGE_STORE || requestCode == REQUEST_CREATE_STORE)
            handleChangeStore(resultData);
    }

    public void notifyListsListeners() {
        mLists.notifyChangeListeners();
    }
    /**
     * Load the list of checklists.
     */
    Thread mLoadThread = null;

    private void handleImport(Uri uri) {
        try {
            if (uri == null)
                return;
            InputStream stream;
            if (Objects.equals(uri.getScheme(), "file")) {
                stream = new FileInputStream(new File((uri.getPath())));
            } else if (Objects.equals(uri.getScheme(), "content")) {
                stream = getContentResolver().openInputStream(uri);
            } else {
                throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
            }
            Checklist newList = new Checklist(mLists, "Unknown");
            newList.fromStream(stream);

            mLists.add(newList);
            Log.d(TAG, "imported list: " + newList.getText());
            mLists.notifyChangeListeners();
            saveAdvised(TAG, "list imported");
            Toast.makeText(this, getString(R.string.import_report, newList.getText()), Toast.LENGTH_LONG).show();
            pushFragment(new ChecklistFragment(newList));
        } catch (Exception e) {
            Log.d(TAG, "import failed " + e.getMessage());
            Toast.makeText(this, R.string.failed_import, Toast.LENGTH_LONG).show();
        }
    }

    public void importList() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_LIST);
    }

    private Checklists loadCache(Checklists cacheLists) {
        cacheLists.clear();
        try {
            if ((FORCE_LOAD_FAIL & 2) != 0)
                throw new Exception("Cache load fail forced");
            else {
                FileInputStream fis = openFileInput(Settings.cacheFile);
                cacheLists.fromStream(fis);
                Log.d(TAG, cacheLists.size() + " lists loaded from cache");
            }
        } catch (Exception ce) {
            Log.d(TAG, "Exception loading from cache " + Settings.cacheFile + ": " + ce);
            runOnUiThread(() -> Toast.makeText(this, R.string.failed_cache_load, Toast.LENGTH_LONG).show());
        }
        return cacheLists;
    }

    private void loadLists() {
        if (mLoadThread != null && mLoadThread.isAlive()) {
            mLoadThread.interrupt();
            mLoadThread = null;
        }

        // Asynchronously load the URI. If the load fails, try the cache
        final Uri uri = Settings.getUri("backingStore");
        if (uri == null)
            return;
        mLoadThread = new Thread(() -> {
            Log.d(TAG, "Starting load thread to load from " + uri);
            try {
                InputStream stream;
                if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                    stream = new FileInputStream(new File((uri.getPath())));
                } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                    stream = getContentResolver().openInputStream(uri);
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
                    Checklists cl = loadCache(new Checklists());
                    Log.d(TAG, "Cache comes from " + cl.getURI());
                    if (cl.isMoreRecentVersionOf(mLists)) {
                        Log.d(TAG, "Cache is more recent");
                        runOnUiThread(() -> Toast.makeText(this, R.string.cache_is_newer, Toast.LENGTH_LONG).show());
                        loadCache(mLists);
                    }
                    runOnUiThread(() -> mLists.notifyChangeListeners());
                }
            } catch (final SecurityException se) {
                // openInputStream denied, reroute through picker to re-establish permissions
                Log.d(TAG, "Security Exception loading " + uri + ": " + se);
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
                        if (Build.VERSION.SDK_INT >= 26)
                            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                        intent.setType("application/json");
                        startActivityForResult(intent, REQUEST_CHANGE_STORE);
                        // onActivityResult will re-load the list in response to a successful
                        // REQUEST_CHANGE_STORE
                    });
                    builder.show();
                });
            } catch (Exception e) {
                Log.d(TAG, "Exception loading from " + uri + ": " + e);
                runOnUiThread(() -> Toast.makeText(this, R.string.failed_uri_load, Toast.LENGTH_LONG).show());
                loadCache(mLists);
                Log.d(TAG, "Cache comes from " + mLists.getURI());
                if (!mLists.getURI().equals(uri.toString()))
                    mLists.clear();
                runOnUiThread(() -> mLists.notifyChangeListeners());
            }
        });
        mLoadThread.start();
    }

    private void handleChangeStore(Intent resultData) {
        Uri newURI = resultData.getData();
        if (newURI == null)
            return;

        Uri oldURI = Settings.getUri(Settings.uri);
        if (!newURI.equals(oldURI)) {
            Settings.setUri(Settings.uri, newURI);

            // Persist granted access across reboots
            int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(newURI, takeFlags);
        }
    }

    /**
     * Hide the current fragment, pushing it onto the stack, then open a new fragment. A neat
     * alternative to dialogs.
     *
     * @param fragment the fragment to switch to
     */
    public void pushFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ftx = fm.beginTransaction();
        // Hide, but don't close, the open fragment (which will always be the tree view)
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
        saveToURI();
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (frag instanceof EntryListFragment)
            ((EntryListFragment)frag).onActivated();
    }

    /**
     * Notify the activity that something has changed, and a save is advised. The cache is
     * always written, but the backing store will only be written after a few minutes
     * so a series of small changes is batched.
     */
    public void saveAdvised(String tag, String why) {
        Log.d(tag, "Save advised due to " + why);
        invalidateOptionsMenu(); // update menu items
        String jsonString;
        try {
            jsonString = mLists.toJSON().toString(1);
        } catch (JSONException je) {
            throw new Error("JSON exception " + je.getMessage());
        }

        // Always save to the cache.
        Log.d(TAG, "Saving to cache");
        try {
            FileOutputStream stream = openFileOutput(Settings.cacheFile, Context.MODE_PRIVATE);
            stream.write(jsonString.getBytes());
            stream.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception saving to cache: " + e);
            Toast.makeText(this, R.string.failed_save_to_cache, Toast.LENGTH_LONG).show();
        }

        saveToURI();
    }

    private void saveToURI() {
        final Uri uri = Settings.getUri(Settings.uri);
        if (uri == null)
            return;
        String jsonString;
        try {
            jsonString = mLists.toJSON().toString(1);
        } catch (JSONException je) {
            throw new Error("JSON exception " + je.getMessage());
        }

        // Launch a thread to do this save, so we don't block the ui thread
        Log.d(TAG, "Saving to " + uri);
        final byte[] data = jsonString.getBytes();

        new Thread(() -> {
            OutputStream stream;
            try {
                String scheme = uri.getScheme();
                if (Objects.equals(scheme, ContentResolver.SCHEME_FILE)) {
                    String path = uri.getPath();
                    stream = new FileOutputStream(new File(path));
                } else if (Objects.equals(scheme, ContentResolver.SCHEME_CONTENT))
                    stream = getContentResolver().openOutputStream(uri);
                else
                    throw new IOException("Unknown uri scheme: " + uri.getScheme());
                if (stream == null)
                    throw new IOException("Stream open failed");
                stream.write(data);
                stream.close();
                Log.d(TAG, "Saved to " + uri);
            } catch (IOException ioe) {
                Log.e(TAG, "Exception while saving to Uri " + ioe.getMessage());
                runOnUiThread(() -> Toast.makeText(this, R.string.failed_save_to_uri, Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}