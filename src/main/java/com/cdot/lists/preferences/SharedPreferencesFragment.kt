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
package com.cdot.lists.preferences

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import com.cdot.lists.Lister
import com.cdot.lists.ListerActivity
import com.cdot.lists.R

/**
 * Handle shared preferences
 */
class SharedPreferencesFragment(private val mLister: Lister) : PreferencesFragment() {
    private fun initBoolPref(name: String) {
        val cbPref = findPreference<CheckBoxPreference>(name)!!
        cbPref.isChecked = mLister.getBool(name)
        cbPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
            Log.d(TAG, "setting $name to $newValue")
            mLister.setBool(name, newValue as Boolean)
            true
        }
    }

    // PreferenceFragmentCompat
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.shared_preferences, rootKey)
        initBoolPref(Lister.PREF_GREY_CHECKED)
        initBoolPref(Lister.PREF_STRIKE_CHECKED)
        initBoolPref(Lister.PREF_LEFT_HANDED)
        initBoolPref(Lister.PREF_ENTIRE_ROW_TOGGLES)
        initBoolPref(Lister.PREF_ALWAYS_SHOW)
        initBoolPref(Lister.PREF_STAY_AWAKE)
        initBoolPref(Lister.PREF_WARN_DUPLICATE)
        val ilPref = findPreference<IntListPreference>(Lister.PREF_TEXT_SIZE_INDEX)!!
        val v = mLister.getInt(Lister.PREF_TEXT_SIZE_INDEX)
        ilPref.value = v.toString()
        ilPref.summary = resources.getStringArray(R.array.text_size_options)[v]
        ilPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference, value: Any ->
            val ival = value.toString().toInt()
            Log.d(TAG, "setting text size to $ival")
            preference.summary = resources.getStringArray(R.array.text_size_options)[ival]
            mLister.setInt(Lister.PREF_TEXT_SIZE_INDEX, ival)
            true
        }
        var pref = findPreference<Preference>("action_change_uri")!!
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            handleStoreClick(Intent.ACTION_OPEN_DOCUMENT, ListerActivity.REQUEST_CHANGE_STORE)
        }
        pref.setTitle(if (mLister.getUri(Lister.PREF_FILE_URI) != null) R.string.action_file_change else R.string.action_file_open)
        pref.setSummary(if (mLister.getUri(Lister.PREF_FILE_URI) != null) R.string.help_file_change else R.string.help_file_open)
        pref = findPreference("action_create_uri")!!
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            handleStoreClick(Intent.ACTION_CREATE_DOCUMENT, ListerActivity.REQUEST_CREATE_STORE)
        }
    }

    private fun handleStoreClick(action: String, request: Int): Boolean {
        val intent = Intent(action)
        intent.flags = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (Build.VERSION.SDK_INT >= 26) {
            val bs = mLister.getUri(Lister.PREF_FILE_URI)
            if (bs != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs)
        }
        intent.type = "*/*" // see https://developer.android.com/guide/components/intents-common
        intent.putExtra(Intent.EXTRA_MIME_TYPES, resources.getStringArray(R.array.share_format_mimetype))
        // The setting of mIssueReports will stay true from now on, but that's OK, it's only purpose
        // is to suppress repeated store alarms during initialisation
        (activity as PreferencesActivity?)!!.mIssueReports = true
        requireActivity().startActivityForResult(intent, request)
        return true
    }

    companion object {
        private val TAG = SharedPreferencesFragment::class.simpleName
    }
}