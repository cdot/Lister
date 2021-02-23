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
import android.view.View
import com.cdot.lists.Lister
import com.cdot.lists.ListerActivity
import com.cdot.lists.model.Checklist

/**
 * Activity used to host preference fragments - supports share preferences and local checklist
 * preferences
 */
class PreferencesActivity : ListerActivity() {
    @JvmField
    var fragment: PreferencesFragment? = null

    // Flag that suppresses reporting during activity onResume/onCreate, so we don't get spammed
    // by startup warnings repeated from the ChecklistsActivity
    @JvmField
    var issuingReports = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        val lister = application as Lister
        val intent = intent
        val uid = intent?.getIntExtra(UID_EXTRA, -1) ?: -1
        if (uid > 0) {
            val list = (lister.lists.findBySessionUID(uid) as Checklist)
            ft.replace(android.R.id.content, ChecklistPreferencesFragment(list).also { fragment = it })
        } else
            ft.replace(android.R.id.content, SharedPreferencesFragment(lister).also { fragment = it })
        ft.commit()
    }

    override val rootView : View?
        get() = fragment!!.rootView

    override fun updateDisplay() {}

    override fun report(code: Int, duration: Int, vararg ps: Any): Boolean {
        if (issuingReports)
            return super.report(code, duration, ps)
        return true
    }

    companion object {
        private val TAG = PreferencesActivity::class.simpleName
    }
}