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
package com.cdot.lists.fragment;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.cdot.lists.R;
import com.cdot.lists.model.Checklist;

/**
 * Handle preferences that are carried in a list
 */
public class ChecklistPreferencesFragment extends PreferenceFragmentCompat {
    private static final String TAG = ChecklistPreferencesFragment.class.getSimpleName();

    Checklist mList;

    ChecklistPreferencesFragment(Checklist list) {
        mList = list;
    }

    @Override // PreferenceFragmentCompat
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.list_preferences, rootKey);

        CheckBoxPreference cbPref = findPreference("moveCheckedItemsToBottom");
        cbPref.setChecked(mList.showCheckedAtEnd);
        cbPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Log.d(TAG, "setting showCheckedAtEnd to " + newValue);
            mList.showCheckedAtEnd = (boolean) newValue;
            return true;
        });

        cbPref = findPreference("autoDeleteCheckedItems");
        cbPref.setChecked(mList.autoDeleteChecked);
        cbPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Log.d(TAG, "setting autoDeleteChecked to " + newValue);
            mList.autoDeleteChecked = (boolean) newValue;
            return true;
        });

        cbPref = findPreference("warnAboutDuplicates");
        cbPref.setChecked(mList.warnAboutDuplicates);
        cbPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Log.d(TAG, "setting warnAboutDuplicates to " + newValue);
            mList.warnAboutDuplicates = (boolean) newValue;
            return true;
        });
    }
}
