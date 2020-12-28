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

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;

import com.cdot.lists.Lister;
import com.cdot.lists.ListerActivity;
import com.cdot.lists.R;
import com.cdot.lists.model.Checklist;

/**
 * Handle preferences that are carried in a list
 */
public class ChecklistPreferencesFragment extends PreferencesFragment {
    private static final String TAG = ChecklistPreferencesFragment.class.getSimpleName();

    Checklist mList;

    public ChecklistPreferencesFragment() {
    }

    public ChecklistPreferencesFragment(Checklist list) {
        mList = list;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt(ListerActivity.UID_EXTRA, mList.getSessionUID());
    }

    @Override // PreferenceFragmentCompat
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.list_preferences, rootKey);

        if (mList == null && savedInstanceState != null) {
            Log.d(TAG, "Recovering list from saved instance state");
            Lister lister = (Lister) getActivity().getApplication();
            int uid = savedInstanceState.getInt(ListerActivity.UID_EXTRA);
            mList = (Checklist) lister.getLists().findBySessionUID(uid);
        }

        if (mList == null)
            throw new Error("Null list");

        for (String k : mList.getFlagNames()) {
            CheckBoxPreference cbPref = findPreference(k);
            if (cbPref != null) {
                cbPref.setChecked(mList.getFlag(k));
                cbPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    Log.d(TAG, "setting " + k + " to " + newValue);
                    if ((boolean)newValue)
                        mList.setFlag(k);
                    else
                        mList.clearFlag(k);
                    ((PreferencesActivity) getActivity()).checkpoint();
                    return true;
                });
            }
        }
    }
}
