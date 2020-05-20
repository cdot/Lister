package com.cdot.lists;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.WebView;

import com.cdot.lists.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class HelpActivity extends AppCompatActivity {
    WebView mWebView;

    /* access modifiers changed from: protected */
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setTitle((int) R.string.action_help);
        WebView webView = new WebView(this);
        this.mWebView = webView;
        webView.getSettings().setBuiltInZoomControls(true);
        this.mWebView.loadUrl("file:///android_asset/html/" + getString(R.string.locale_prefix) + "/help.html");
        setContentView(this.mWebView);
    }

    public static String readRawTextFile(Context context, int i) {
        InputStream openRawResource = context.getResources().openRawResource(i);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            for (int read = openRawResource.read(); read != -1; read = openRawResource.read()) {
                byteArrayOutputStream.write(read);
            }
            openRawResource.close();
            return byteArrayOutputStream.toString();
        } catch (IOException unused) {
            return null;
        }
    }
}
