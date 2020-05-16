package com.cdot.lists;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.Spinner;

import com.cdot.lists.databinding.SettingsActivityBinding;

public class SettingsActivity extends Activity implements AdapterView.OnItemSelectedListener {
    private static final int REQUEST_CHANGE_STORE = 1;
    private static final int REQUEST_CREATE_STORE = 2;

    CheckBox mAddNewItemsAtTopOfListCB;
    CheckBox mAutoDeleteCB;
    CheckBox mCheckBoxOnLeftSideCB;
    CheckBox mDarkBackgroundCB;
    CheckBox mDimCheckedItemsCB;
    CheckBox mEntireRowTogglesCheckboxCB;
    CheckBox mMoveCheckedItemsToBottomCB;
    CheckBox mOpenLatestListAtStartupCB;
    CheckBox mShowListInFrontOfLockScreenCB;
    CheckBox mStrikeThroughCheckedItemsCB;
    Spinner mTextSizeSpinner;
    CheckBox mWarnAboutDuplicatesCB;

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        SettingsActivityBinding binding = SettingsActivityBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        mDimCheckedItemsCB = binding.DimCheckedItemsCB;
        mStrikeThroughCheckedItemsCB = binding.StrikeThroughCheckedItemsCB;
        mMoveCheckedItemsToBottomCB = binding.MoveCheckedItemsToBottomCB;
        mCheckBoxOnLeftSideCB = binding.CheckBoxOnLeftSideCB;
        mAutoDeleteCB = binding.AutoDeleteCB;
        mEntireRowTogglesCheckboxCB = binding.EntireRowTogglesCheckboxCB;
        mAddNewItemsAtTopOfListCB = binding.AddNewItemsAtTopOfListCB;
        mShowListInFrontOfLockScreenCB = binding.ShowListInFrontOfLockScreenCB;
        mOpenLatestListAtStartupCB = binding.OpenLatestListAtStartupCB;
        mWarnAboutDuplicatesCB = binding.WarnAboutDuplicatesCB;
        Spinner spinner = binding.TextSizeSpinner;
        mTextSizeSpinner = spinner;
        spinner.setOnItemSelectedListener(this);

        mDimCheckedItemsCB.setChecked(Settings.getBool(Settings.greyCheckedItems));
        mStrikeThroughCheckedItemsCB.setChecked(Settings.getBool(Settings.strikeThroughCheckedItems));
        mMoveCheckedItemsToBottomCB.setChecked(Settings.getBool(Settings.moveCheckedItemsToBottom));
        mCheckBoxOnLeftSideCB.setChecked(Settings.getBool(Settings.checkBoxOnLeftSide));
        mAutoDeleteCB.setChecked(Settings.getBool(Settings.autoDeleteCheckedItems));
        mEntireRowTogglesCheckboxCB.setChecked(Settings.getBool(Settings.entireRowTogglesItem));
        mAddNewItemsAtTopOfListCB.setChecked(Settings.getBool(Settings.addNewItemsAtTopOfList));
        mShowListInFrontOfLockScreenCB.setChecked(Settings.getBool(Settings.showListInFrontOfLockScreen));
        mOpenLatestListAtStartupCB.setChecked(Settings.getBool(Settings.openLatestListAtStartup));
        mWarnAboutDuplicatesCB.setChecked(Settings.getBool(Settings.warnAboutDuplicates));
        mTextSizeSpinner.setSelection(Settings.getInt(Settings.textSizeIndex));
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == Activity.RESULT_OK && resultData != null) {
            if (requestCode == REQUEST_CHANGE_STORE) {
                Uri bs = Settings.getUri(Settings.backingStore);
                Uri nbs = resultData.getData();
                if (!nbs.equals(bs))
                    Settings.setUri(Settings.backingStore, nbs);
            }
        }
    }

    public void changeStoreClicked(View view) {
        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CHANGE_STORE);
    }

    public void createStoreClicked(View view) {
        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CHANGE_STORE);
    }

    // Checkbox click handlers, invoked from resource

    public void dimCheckedItemsCBClicked(View view) {
        Settings.setBool(Settings.greyCheckedItems, mDimCheckedItemsCB.isChecked());
    }

    public void moveCheckedItemsToBottomCBClicked(View view) {
        Settings.setBool(Settings.moveCheckedItemsToBottom, mMoveCheckedItemsToBottomCB.isChecked());
    }

    public void checkBoxOnLeftSideCBClicked(View view) {
        Settings.setBool(Settings.checkBoxOnLeftSide, mCheckBoxOnLeftSideCB.isChecked());
    }

    public void autoDeleteCBClicked(View view) {
        Settings.setBool(Settings.autoDeleteCheckedItems, mAutoDeleteCB.isChecked());
    }

    public void entireRowTogglesCheckboxCBClicked(View view) {
        Settings.setBool(Settings.entireRowTogglesItem, mEntireRowTogglesCheckboxCB.isChecked());
    }

    public void strikeThroughCheckedItemsCBClicked(View view) {
        Settings.setBool(Settings.strikeThroughCheckedItems, mStrikeThroughCheckedItemsCB.isChecked());
    }

    public void addNewItemsAtTopOfListCBClicked(View view) {
        Settings.setBool(Settings.addNewItemsAtTopOfList, mAddNewItemsAtTopOfListCB.isChecked());
    }

    public void showListInFrontOfLockScreenCBClicked(View view) {
        Settings.setBool(Settings.showListInFrontOfLockScreen, mShowListInFrontOfLockScreenCB.isChecked());
    }

    public void openLatestListAtStartupCBClicked(View view) {
        Settings.setBool(Settings.openLatestListAtStartup, mOpenLatestListAtStartupCB.isChecked());
    }

    public void warnAboutDuplicatesCBClicked(View view) {
        Settings.setBool(Settings.warnAboutDuplicates, mWarnAboutDuplicatesCB.isChecked());
    }

    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
        if (adapterView == mTextSizeSpinner)
            Settings.setInt(Settings.textSizeIndex, i);
    }
}
