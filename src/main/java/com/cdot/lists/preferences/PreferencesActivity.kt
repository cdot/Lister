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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import com.cdot.lists.Lister
import com.cdot.lists.ListerActivity
import com.cdot.lists.model.Checklist

/**
 * Activity used to host preference fragments
 */
class PreferencesActivity : ListerActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    var mFragment: PreferencesFragment? = null

    // Flag that suppresses reporting during activity onResume/onCreate, so we don't get spammed
    // by startup warnings repeated from the ChecklistsActivity
    @JvmField
    var mIssueReports = false

    // AppCompatActivity
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val mFragmentManager = supportFragmentManager
        val mFragmentTransaction = mFragmentManager.beginTransaction()
        val lister = application as Lister
        val intent = intent
        val uid = intent?.getIntExtra(UID_EXTRA, -1) ?: -1
        if (uid > 0) {
            val list = (lister.lists.findBySessionUID(uid) as Checklist)
            mFragmentTransaction.replace(android.R.id.content, ChecklistPreferencesFragment(list).also { mFragment = it })
        } else mFragmentTransaction.replace(android.R.id.content, SharedPreferencesFragment(lister).also { mFragment = it })
        mFragmentTransaction.commit()
    }

    // ListerActivity
    override val rootView : View?
        get() = mFragment!!.rootView

    // No lists, so nothing to do
    override fun updateDisplay() {}

    // ListerActivity
    override fun report(code: Int, duration: Int, vararg ps: Any): Boolean {
        if (mIssueReports)
            return super.report(code, duration, ps)
        return true
    }

    // override AppCompatActivity
    public override fun onPause() {
        lister.prefs?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    // override AppCompatActivity
    public override fun onResume() {
        super.onResume()
        lister.prefs?.registerOnSharedPreferenceChangeListener(this)
    }

    // override SharedPreferences.OnSharedPreferencesListener
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            Lister.PREF_ALWAYS_SHOW -> configureShowOverLockScreen()
            Lister.PREF_STAY_AWAKE -> configureStayAwake()
        }
    }

    companion object {
        private val TAG = PreferencesActivity::class.simpleName
    }
}