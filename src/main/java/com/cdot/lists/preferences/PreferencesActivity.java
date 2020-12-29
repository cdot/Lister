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
package com.cdot.lists.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.cdot.lists.Lister;
import com.cdot.lists.ListerActivity;
import com.cdot.lists.R;
import com.cdot.lists.model.Checklist;
import com.google.android.material.snackbar.Snackbar;

/**
 * Activity used to host preference fragments
 * Use startActivityForResult to know when the back is pressed
 */
public class PreferencesActivity extends ListerActivity {
    private static final String TAG = PreferencesActivity.class.getSimpleName();

    PreferencesFragment mFragment;
    // Flag that suppresses reporting during activity onResume/onCreate, so we don't get spammed
    // by startup warnings repeated from the ChecklistsActivity
    boolean mIssueReports = false;

    @Override // AppCompatActivity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager mFragmentManager = getSupportFragmentManager();
        FragmentTransaction mFragmentTransaction = mFragmentManager.beginTransaction();
        Lister lister = (Lister) getApplication();
        Intent intent = getIntent();
        int uid = (intent == null) ? -1 : intent.getIntExtra(UID_EXTRA, -1);
        if (uid > 0) {
            Checklist list = (Checklist) lister.getLists().findBySessionUID(uid);
            if (list == null)
                throw new Error("Could not find list " + uid);
            mFragmentTransaction.replace(android.R.id.content, mFragment = new ChecklistPreferencesFragment(list));
        } else
            mFragmentTransaction.replace(android.R.id.content, mFragment = new SharedPreferencesFragment(lister));

        mFragmentTransaction.commit();
    }

    @Override // ListerActivity
    protected View getRootView() {
        // This activity has no views other than those provided by the fragment
        return mFragment.getRootView();
    }

    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        handleStoreIntent(requestCode, intent);
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override // ListerActivity
    public boolean report(int code, int duration) {
        if (mIssueReports) {
            runOnUiThread(() -> Snackbar.make(getRootView(), code, duration)
                    .setAction(R.string.close, x -> {
                        // Responds to click on the action
                    }).show());
        }
        return true;
    }
}
