/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.os.Bundle;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Simple activity to view help information stored in an html file
 */
public class HelpActivity extends AppCompatActivity {
    WebView mWebView;

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String page = getIntent().getStringExtra("page");
        setTitle(R.string.action_help);
        WebView webView = new WebView(this);
        this.mWebView = webView;
        webView.getSettings().setBuiltInZoomControls(true);
        this.mWebView.loadUrl("file:///android_asset/html/" + getString(R.string.locale_prefix) + "/" + page + ".html");
        setContentView(this.mWebView);
    }
}
