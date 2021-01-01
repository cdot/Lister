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

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

/**
 * Customisation of ListPreference to persist integer values rather than the string values that
 * ListPreference insists on.
 */
internal class IntListPreference
(context: Context?, attrs: AttributeSet?) : ListPreference(context, attrs) {

    // Persist the string value managed by the list as an int.
    override fun persistString(value: String): Boolean {
        val intValue = value.toInt()
        return persistInt(intValue) // from Preference
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        val intDefaultReturnValue = defaultReturnValue?.toInt() ?: 0
        val intValue = getPersistedInt(intDefaultReturnValue) // from Preference
        return intValue.toString()
    }
}