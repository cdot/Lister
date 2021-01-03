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

import android.os.Bundle
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import com.cdot.lists.ListerActivity
import com.cdot.lists.R
import com.cdot.lists.model.Checklist

/**
 * Handle preferences that are carried in a list
 */
class ChecklistPreferencesFragment(private var theList: Checklist) : PreferencesFragment() {

    override fun onSaveInstanceState(state: Bundle) {
        state.putInt(ListerActivity.UID_EXTRA, theList.sessionUID)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.list_preferences, rootKey)
        for (k in theList.flagNames) {
            val cbPref = findPreference<CheckBoxPreference>(k)
            if (cbPref != null) {
                cbPref.isChecked = theList.getFlag(k)
                cbPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    Log.d(TAG, "setting $k to $newValue")
                    if (newValue as Boolean) theList.setFlag(k) else theList.clearFlag(k)
                    (activity as PreferencesActivity?)!!.checkpoint()
                    true
                }
            }
        }
    }

    companion object {
        private val TAG = ChecklistPreferencesFragment::class.simpleName
    }
}