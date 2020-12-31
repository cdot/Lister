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
package com.cdot.lists

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cdot.lists.databinding.HelpActivityBinding
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * View help information stored in an html file
 */
class HelpActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val asset = intent.getIntExtra(ASSET_EXTRA, 0)
        val binding = HelpActivityBinding.inflate(layoutInflater)
        binding.webview.settings.builtInZoomControls = true
        // Get the right HTML for the current locale
        try {
            val `is` = resources.openRawResource(asset)
            val br = BufferedReader(InputStreamReader(`is`))
            var data: String?
            val sb = StringBuilder()
            while (br.readLine().also { data = it } != null) sb.append(data).append("\n")
            data = sb.toString()
            binding.webview.loadDataWithBaseURL("file:///android_asset/", data!!, "text/html", "utf-8", null)
        } catch (ieo: IOException) {
        }
        setContentView(binding.root)
    }

    companion object {
        @JvmField
        val ASSET_EXTRA = HelpActivity::class.java.canonicalName + ".asset"
    }
}