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
package com.cdot.lists;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cdot.lists.databinding.HelpActivityBinding;

/**
 * View help information stored in an html file
 */
public class HelpActivity extends AppCompatActivity {
    public static final String ASSET_EXTRA = HelpActivity.class.getCanonicalName() + ".asset";

    public HelpActivity() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String asset;
        Intent intent = getIntent();
        if (intent != null)
            asset = intent.getStringExtra(ASSET_EXTRA);
        else
            asset = savedInstanceState.getString(ASSET_EXTRA);
        HelpActivityBinding binding = HelpActivityBinding.inflate(getLayoutInflater());
        binding.webview.getSettings().setBuiltInZoomControls(true);
        binding.webview.loadUrl("file:///android_asset/html/" + getString(R.string.locale_prefix) + "/" + asset + ".html");
        setContentView(binding.getRoot());
    }
}
