package com.cdot.lists;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.WebView;

public class HelpActivity extends AppCompatActivity {
    WebView mWebView;

    /* access modifiers changed from: protected */
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setTitle(R.string.action_help);
        WebView webView = new WebView(this);
        this.mWebView = webView;
        webView.getSettings().setBuiltInZoomControls(true);
        this.mWebView.loadUrl("file:///android_asset/html/" + getString(R.string.locale_prefix) + "/help.html");
        setContentView(this.mWebView);
    }
}
