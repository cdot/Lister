package com.cdot.lists;

import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

abstract class ActivityWithSettings extends AppCompatActivity {
    private static final String TAG = "ActivityWithSettings";

    /**
     * Reload whatever is displayed by this activity after settings change
     */
    protected abstract void onSettingsChanged();

    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        if (requestCode != Settings.REQUEST_PREFERENCES)
            return;

        if (resultData != null) {
            Uri newURI = resultData.getData();

            if (newURI == null)
                return;

            Uri oldURI = Settings.getUri(Settings.backingStore);
            if (!newURI.equals(oldURI)) {
                Settings.setUri(Settings.backingStore, newURI);

                // Persist granted access across reboots
                int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(newURI, takeFlags);
            }
        }

        onSettingsChanged();
    }
}
