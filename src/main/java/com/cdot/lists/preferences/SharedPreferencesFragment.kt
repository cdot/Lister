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
import android.content.Intent.EXTRA_MIME_TYPES
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import com.cdot.lists.BuildConfig
import com.cdot.lists.Lister
import com.cdot.lists.ListerActivity
import com.cdot.lists.R

/**
 * Handle shared preferences
 */
class SharedPreferencesFragment(private val lister: Lister) : PreferencesFragment() {
    private var aboutClickCountdown = 0
    private var lastAboutClick = 0L

    private fun initBoolPref(name: String) {
        val cbPref = findPreference<CheckBoxPreference>(name)!!
        cbPref.isChecked = lister.getBool(name)
        cbPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            Log.d(TAG, "setting $name to $newValue")
            lister.setBool(name, newValue as Boolean)
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
        initBoolPref(Lister.PREF_WARN_DUPLICATE)
        initBoolPref(Lister.PREF_DISABLE_CACHE)
        initBoolPref(Lister.PREF_DISABLE_FILE)

        val textSizePref = findPreference<IntListPreference>(Lister.PREF_TEXT_SIZE_INDEX)!!
        val textSizeIndex = lister.getInt(Lister.PREF_TEXT_SIZE_INDEX)
        textSizePref.intValue = textSizeIndex
        textSizePref.summary = resources.getStringArray(R.array.text_size_options)[textSizeIndex]
        textSizePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference, value: Any ->
            val ival = value.toString().toInt()
            Log.d(TAG, "setting text size to $ival")
            preference.summary = resources.getStringArray(R.array.text_size_options)[ival]
            lister.setInt(Lister.PREF_TEXT_SIZE_INDEX, ival)
            true
        }

        val changeStorePref = findPreference<Preference>("action_change_uri")!!
        changeStorePref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            handleStoreClick(Intent.ACTION_OPEN_DOCUMENT, ListerActivity.REQUEST_CHANGE_STORE)
        }
        changeStorePref.setTitle(if (lister.getUri(Lister.PREF_FILE_URI) != null) R.string.action_file_change else R.string.action_file_open)
        changeStorePref.setSummary(if (lister.getUri(Lister.PREF_FILE_URI) != null) R.string.help_file_change else R.string.help_file_open)

        val createStorePref = findPreference<Preference>("action_create_uri")!!
        createStorePref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            handleStoreClick(Intent.ACTION_CREATE_DOCUMENT, ListerActivity.REQUEST_CREATE_STORE)
        }

        findPreference<CheckBoxPreference>(Lister.PREF_DISABLE_CACHE)?.isVisible = lister.getBool(Lister.PREF_DEBUG)
        findPreference<CheckBoxPreference>(Lister.PREF_DISABLE_FILE)?.isVisible = lister.getBool(Lister.PREF_DEBUG)

        // Tooch the about button repeatedly until it switches on/off debugger state
        val aboutPref = findPreference<Preference>("action_about")!!
        val usualMess = resources.getString(R.string.app_name) + " " + resources.getString(R.string.version_info, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TIME)
        aboutPref.summary = usualMess
        aboutPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val now = System.currentTimeMillis()
            if (now - lastAboutClick < 1000) {
                val mess = if (--aboutClickCountdown <= 0) {
                    lister.setBool(Lister.PREF_DEBUG, !lister.getBool(Lister.PREF_DEBUG))
                    findPreference<CheckBoxPreference>(Lister.PREF_DISABLE_CACHE)?.isVisible = lister.getBool(Lister.PREF_DEBUG)
                    findPreference<CheckBoxPreference>(Lister.PREF_DISABLE_FILE)?.isVisible = lister.getBool(Lister.PREF_DEBUG)
                    resources.getString(R.string.debug_toggled, lister.getBool(Lister.PREF_DEBUG).toString())
                } else
                    resources.getString(R.string.debug_countdown, aboutClickCountdown, (!lister.getBool(Lister.PREF_DEBUG)).toString())
                Log.d(TAG, mess)
                //Toast.makeText(activity, mess, Toast.LENGTH_SHORT).show()
                aboutPref.summary = mess
            } else {
                aboutClickCountdown = 4
                aboutPref.summary = usualMess
            }
            lastAboutClick = now
            true
        }
    }

    private fun handleStoreClick(action: String, request: Int): Boolean {
        val intent = Intent(action)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.flags = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (Build.VERSION.SDK_INT >= 26) {
            val bs = lister.getUri(Lister.PREF_FILE_URI)
            if (bs != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs)
        }
        // You would have thought to use intent.type = "application/json", but files saved using this
        // app are not then visible to ACTION_OPEN_DOCUMENT. I assume the mime type is not recorded.
        intent.type = "*/*" // see https://developer.android.com/guide/components/intents-common
        intent.putExtra(EXTRA_MIME_TYPES, resources.getStringArray(R.array.file_format_mimetype))

        // The setting of mIssueReports will stay true from now on, but that's OK, it's only purpose
        // is to suppress repeated store alarms during PreferencesActivity+SharedPreferencesFragment startup
        (activity as PreferencesActivity?)!!.issuingReports = true
        val act = requireActivity()
        act.startActivityForResult(intent, request)
        return true
    }

    companion object {
        private val TAG = SharedPreferencesFragment::class.simpleName
    }
}