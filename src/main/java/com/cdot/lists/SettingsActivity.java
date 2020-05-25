package com.cdot.lists;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.AdapterView;

import com.cdot.lists.databinding.SettingsActivityBinding;

public class SettingsActivity extends Activity implements AdapterView.OnItemSelectedListener {
    private static final int REQUEST_CHANGE_STORE = 1;
    private static final int REQUEST_CREATE_STORE = 2;

    SettingsActivityBinding mBinding;
    
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mBinding = SettingsActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mBinding.TextSizeSpinner.setOnItemSelectedListener(this);
        mBinding.DimCheckedItemsCB.setChecked(Settings.getBool(Settings.greyCheckedItems));
        mBinding.StrikeThroughCheckedItemsCB.setChecked(Settings.getBool(Settings.strikeThroughCheckedItems));
        mBinding.MoveCheckedItemsToBottomCB.setChecked(Settings.getBool(Settings.moveCheckedItemsToBottom));
        mBinding.CheckBoxOnLeftSideCB.setChecked(Settings.getBool(Settings.checkBoxOnLeftSide));
        mBinding.AutoDeleteCB.setChecked(Settings.getBool(Settings.autoDeleteCheckedItems));
        mBinding.EntireRowTogglesCheckboxCB.setChecked(Settings.getBool(Settings.entireRowTogglesItem));
        mBinding.AddNewItemsAtTopOfListCB.setChecked(Settings.getBool(Settings.addNewItemsAtTopOfList));
        mBinding.ShowListInFrontOfLockScreenCB.setChecked(Settings.getBool(Settings.showListInFrontOfLockScreen));
        mBinding.OpenLatestListAtStartupCB.setChecked(Settings.getBool(Settings.openLatestListAtStartup));
        mBinding.WarnAboutDuplicatesCB.setChecked(Settings.getBool(Settings.warnAboutDuplicates));
        mBinding.TextSizeSpinner.setSelection(Settings.getInt(Settings.textSizeIndex));
        Uri uri = Settings.getUri(Settings.backingStore);
        mBinding.BackingStoreURI.setText(uri != null ? uri.toString() : getString(R.string.not_set));
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == Activity.RESULT_OK && resultData != null) {
            if (requestCode == REQUEST_CHANGE_STORE || requestCode == REQUEST_CREATE_STORE) {
                Uri bs = Settings.getUri(Settings.backingStore);
                Uri nbs = resultData.getData();
                if (!nbs.equals(bs))
                    Settings.setUri(Settings.backingStore, nbs);

                // Clear the cache
                deleteFile(Settings.cacheFile);

                // Persist granted access across reboots
                if (requestCode == REQUEST_CREATE_STORE) {
                    final int takeFlags = resultData.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(bs, takeFlags);
                }
                mBinding.BackingStoreURI.setText(nbs.toString());
            }
        }
    }

    public void changeStoreClicked(View view) {
        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CHANGE_STORE);
    }

    public void createStoreClicked(View view) {
        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CREATE_STORE);
    }

    // Checkbox click handlers, invoked from resource

    public void dimCheckedItemsCBClicked(View view) {
        Settings.setBool(Settings.greyCheckedItems, mBinding.DimCheckedItemsCB.isChecked());
    }

    public void moveCheckedItemsToBottomCBClicked(View view) {
        Settings.setBool(Settings.moveCheckedItemsToBottom, mBinding.MoveCheckedItemsToBottomCB.isChecked());
    }

    public void checkBoxOnLeftSideCBClicked(View view) {
        Settings.setBool(Settings.checkBoxOnLeftSide, mBinding.CheckBoxOnLeftSideCB.isChecked());
    }

    public void autoDeleteCBClicked(View view) {
        Settings.setBool(Settings.autoDeleteCheckedItems, mBinding.AutoDeleteCB.isChecked());
    }

    public void entireRowTogglesCheckboxCBClicked(View view) {
        Settings.setBool(Settings.entireRowTogglesItem, mBinding.EntireRowTogglesCheckboxCB.isChecked());
    }

    public void strikeThroughCheckedItemsCBClicked(View view) {
        Settings.setBool(Settings.strikeThroughCheckedItems, mBinding.StrikeThroughCheckedItemsCB.isChecked());
    }

    public void addNewItemsAtTopOfListCBClicked(View view) {
        Settings.setBool(Settings.addNewItemsAtTopOfList, mBinding.AddNewItemsAtTopOfListCB.isChecked());
    }

    public void showListInFrontOfLockScreenCBClicked(View view) {
        Settings.setBool(Settings.showListInFrontOfLockScreen, mBinding.ShowListInFrontOfLockScreenCB.isChecked());
    }

    public void openLatestListAtStartupCBClicked(View view) {
        Settings.setBool(Settings.openLatestListAtStartup, mBinding.OpenLatestListAtStartupCB.isChecked());
    }

    public void warnAboutDuplicatesCBClicked(View view) {
        Settings.setBool(Settings.warnAboutDuplicates, mBinding.WarnAboutDuplicatesCB.isChecked());
    }

    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
        if (adapterView == mBinding.TextSizeSpinner)
            Settings.setInt(Settings.textSizeIndex, i);
    }
}
