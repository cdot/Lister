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

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.cdot.lists.Lister;
import com.cdot.lists.MainActivity;
import com.cdot.lists.R;

/**
 * Handle shared preferences
 */
public class SharedPreferencesFragment extends PreferenceFragmentCompat {
    private static final String TAG = SharedPreferencesFragment.class.getSimpleName();

    protected MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    private Lister getLister() {
        return getMainActivity().getLister();
    }

    private void initBoolPref(String name) {
        CheckBoxPreference cbPref = findPreference(name);
        cbPref.setChecked(getLister().getBool(name));
        cbPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Log.d(TAG, "setting " + name + " to " + newValue);
            getLister().setBool(name, (boolean) newValue);
            return true;
        });
    }

    @Override // PreferenceFragmentCompat
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.shared_preferences, rootKey);

        initBoolPref(Lister.PREF_DEBUG);
        initBoolPref(Lister.PREF_GREY_CHECKED);
        initBoolPref(Lister.PREF_STRIKE_CHECKED);
        initBoolPref(Lister.PREF_LEFT_HANDED);
        initBoolPref(Lister.PREF_ENTIRE_ROW_TOGGLES);
        initBoolPref(Lister.PREF_ALWAYS_SHOW);
        initBoolPref(Lister.PREF_STAY_AWAKE);

        IntListPreference ilPref = findPreference(Lister.PREF_TEXT_SIZE_INDEX);
        int val = getLister().getInt(Lister.PREF_TEXT_SIZE_INDEX);
        ilPref.setValue(Integer.toString(val));
        ilPref.setSummary(getResources().getStringArray(R.array.text_size_options)[val]);
        ilPref.setOnPreferenceChangeListener((preference, value) -> {
            int ival = Integer.parseInt(value.toString());
            Log.d(TAG, "setting text size to " + ival);
            preference.setSummary(getResources().getStringArray(R.array.text_size_options)[ival]);
            getLister().setInt(Lister.PREF_TEXT_SIZE_INDEX, ival);
            return true;
        });

        Preference pref = findPreference("action_change_uri");
        pref.setOnPreferenceClickListener(this::changeStoreClicked);

        pref = findPreference("action_create_uri");
        pref.setOnPreferenceClickListener(this::createStoreClicked);
    }

    @Override // Fragment
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if ((requestCode == MainActivity.REQUEST_CHANGE_STORE
                || requestCode == Lister.REQUEST_CREATE_STORE)
                && resultCode == AppCompatActivity.RESULT_OK && resultData != null) {
            Uri cur = getLister().getUri(Lister.PREF_URI);
            Uri neu = resultData.getData();
            if (neu != null && !neu.equals(cur) || neu == null && cur != null) {
                getLister().setUri(Lister.PREF_URI, neu);
                // Pass the request on to MainActivity for it to handle the store change
                getMainActivity().onActivityResult(requestCode, resultCode, resultData);
            }
        }
    }

    // Invoked from resource
    public boolean changeStoreClicked(Preference view) {
        Uri bs = getLister().getUri(Lister.PREF_URI);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, MainActivity.REQUEST_CHANGE_STORE);
        return true;
    }

    // Invoked from resource
    public boolean createStoreClicked(Preference view) {
        Uri bs = getLister().getUri(Lister.PREF_URI);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, Lister.REQUEST_CREATE_STORE);
        return true;
    }
}
