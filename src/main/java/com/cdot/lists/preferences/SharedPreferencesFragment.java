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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import com.cdot.lists.Lister;
import com.cdot.lists.ListerActivity;
import com.cdot.lists.R;

/**
 * Handle shared preferences
 */
public class SharedPreferencesFragment extends PreferencesFragment {
    private static final String TAG = SharedPreferencesFragment.class.getSimpleName();
    private final Lister mLister;

    public SharedPreferencesFragment(Lister lister) {
        mLister = lister;
    }

    private void initBoolPref(String name) {
        CheckBoxPreference cbPref = findPreference(name);
        cbPref.setChecked(mLister.getBool(name));
        cbPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Log.d(TAG, "setting " + name + " to " + newValue);
            mLister.setBool(name, (boolean) newValue);
            return true;
        });
    }

    @Override // PreferenceFragmentCompat
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.shared_preferences, rootKey);

        initBoolPref(Lister.PREF_GREY_CHECKED);
        initBoolPref(Lister.PREF_STRIKE_CHECKED);
        initBoolPref(Lister.PREF_LEFT_HANDED);
        initBoolPref(Lister.PREF_ENTIRE_ROW_TOGGLES);
        initBoolPref(Lister.PREF_ALWAYS_SHOW);
        initBoolPref(Lister.PREF_STAY_AWAKE);
        initBoolPref(Lister.PREF_WARN_DUPLICATE);

        IntListPreference ilPref = findPreference(Lister.PREF_TEXT_SIZE_INDEX);
        int val = mLister.getInt(Lister.PREF_TEXT_SIZE_INDEX);
        ilPref.setValue(Integer.toString(val));
        ilPref.setSummary(getResources().getStringArray(R.array.text_size_options)[val]);
        ilPref.setOnPreferenceChangeListener((preference, value) -> {
            int ival = Integer.parseInt(value.toString());
            Log.d(TAG, "setting text size to " + ival);
            preference.setSummary(getResources().getStringArray(R.array.text_size_options)[ival]);
            mLister.setInt(Lister.PREF_TEXT_SIZE_INDEX, ival);
            return true;
        });

        Preference pref = findPreference("action_change_uri");
        pref.setOnPreferenceClickListener(v -> handleStoreClick(Intent.ACTION_OPEN_DOCUMENT, ListerActivity.REQUEST_CHANGE_STORE));
        pref.setVisible(mLister.getUri(Lister.PREF_URI) != null);

        pref = findPreference("action_create_uri");
        pref.setOnPreferenceClickListener(v -> handleStoreClick(Intent.ACTION_CREATE_DOCUMENT, ListerActivity.REQUEST_CREATE_STORE));
    }

    private boolean handleStoreClick(String action, int request) {
        Intent intent = new Intent(action);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= 26) {
            Uri bs = mLister.getUri(Lister.PREF_URI);
            if (bs != null)
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        }
        intent.setType("application/json");
        getActivity().startActivityForResult(intent, request);
        return true;
    }
}
