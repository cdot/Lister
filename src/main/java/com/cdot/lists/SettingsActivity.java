/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.AdapterView;

import com.cdot.lists.databinding.SettingsActivityBinding;

/**
 * This activity is invoked using startActivityForResult, and it will return a RESULT_OK if and only
 * if a new list store has been attached. Otherwise it will return RESULT_CANCELED
 */
public class SettingsActivity extends ActivityWithSettings implements AdapterView.OnItemSelectedListener {

    static final String ENABLE_GENERAL_SETTINGS = "com.cdot.lists.ENABLE_GENERAL_SETTINGS";

    SettingsActivityBinding mBinding;

    @Override // Activity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mBinding = SettingsActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mBinding.textSizeSpinner.setOnItemSelectedListener(this);
        mBinding.greyOutChecked.setChecked(Settings.getBool(Settings.greyChecked));
        mBinding.strikeChecked.setChecked(Settings.getBool(Settings.strikeThroughChecked));
        mBinding.forceAlphaSort.setChecked(Settings.getBool(Settings.forceAlphaSort));
        mBinding.showCheckedAtEnd.setChecked(Settings.getBool(Settings.showCheckedAtEnd));
        mBinding.leftHandOperation.setChecked(Settings.getBool(Settings.leftHandOperation));
        mBinding.autoDeleteChecked.setChecked(Settings.getBool(Settings.autoDeleteChecked));
        mBinding.entireRowToggles.setChecked(Settings.getBool(Settings.entireRowTogglesItem));

        mBinding.alwaysShow.setChecked(Settings.getBool(Settings.alwaysShow));
        mBinding.openLatest.setChecked(Settings.getBool(Settings.openLatest));
        mBinding.warnAboutDuplicates.setChecked(Settings.getBool(Settings.warnAboutDuplicates));
        mBinding.textSizeSpinner.setSelection(Settings.getInt(Settings.textSizeIndex));
        Uri uri = Settings.getUri(Settings.backingStore);
        mBinding.backingStoreUri.setText(uri != null ? uri.toString() : getString(R.string.not_set));

        findViewById(R.id.general_settings).setVisibility((getIntent().getBooleanExtra(ENABLE_GENERAL_SETTINGS, false)) ? View.VISIBLE : View.GONE);

        // Default result is cancelled, unless the data store is changed and lists need to be
        // reloaded in which case it's RESULT_OK
        setResult(RESULT_CANCELED);
    }

    @Override // implement ActivityWithSettings
    protected void onSettingsChanged() {
    }

    @Override // ActivityWithSettings
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        boolean bsChanged = false;
        if (requestCode == Settings.REQUEST_CHANGE_STORE || requestCode == Settings.REQUEST_CREATE_STORE) {
            // Change requestCode to signal to ActivityWithSettings that preferences have changed
            requestCode = Settings.REQUEST_PREFERENCES;
            bsChanged = true;
        }

        // Handle REQUEST_PREFERENCES
        super.onActivityResult(requestCode, resultCode, resultData);

        if (bsChanged && resultCode == RESULT_OK) {
            mBinding.backingStoreUri.setText(Settings.getUri(Settings.backingStore).toString());
            // Only ever return RESULT_OK when the store has been changed
            setResult(RESULT_OK);
        }
    }

    // Invoked from resource
    public void changeStoreClicked(View view) {

        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, Settings.REQUEST_CHANGE_STORE);
    }

    // Invoked from resource
    public void createStoreClicked(View view) {
        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, Settings.REQUEST_CREATE_STORE);
    }

    // Checkbox click handlers, invoked from resource

    public void greyOutCheckedItemsClicked(View view) {
        Settings.setBool(Settings.greyChecked, mBinding.greyOutChecked.isChecked());
    }

    public void showCheckedAtEndClicked(View view) {
        Settings.setBool(Settings.showCheckedAtEnd, mBinding.showCheckedAtEnd.isChecked());
    }

    public void forceAlphaSortClicked(View view) {
        Settings.setBool(Settings.forceAlphaSort, mBinding.forceAlphaSort.isChecked());
    }

    public void leftHandOperationClicked(View view) {
        Settings.setBool(Settings.leftHandOperation, mBinding.leftHandOperation.isChecked());
    }

    public void autoDeleteCheckedItemsClicked(View view) {
        Settings.setBool(Settings.autoDeleteChecked, mBinding.autoDeleteChecked.isChecked());
    }

    public void entireRowTogglesClicked(View view) {
        Settings.setBool(Settings.entireRowTogglesItem, mBinding.entireRowToggles.isChecked());
    }

    public void strikeCheckedItemsClicked(View view) {
        Settings.setBool(Settings.strikeThroughChecked, mBinding.strikeChecked.isChecked());
    }

    public void alwaysShowClicked(View view) {
        Settings.setBool(Settings.alwaysShow, mBinding.alwaysShow.isChecked());
    }

    public void openLatestClicked(View view) {
        Settings.setBool(Settings.openLatest, mBinding.openLatest.isChecked());
    }

    public void warnAboutDuplicatesClicked(View view) {
        Settings.setBool(Settings.warnAboutDuplicates, mBinding.warnAboutDuplicates.isChecked());
    }

    @Override // AdapterView.OnItemSelectedListener
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @Override // AdapterView.OnItemSelectedListener
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
        if (adapterView == mBinding.textSizeSpinner)
            Settings.setInt(Settings.textSizeIndex, i);
    }
}
