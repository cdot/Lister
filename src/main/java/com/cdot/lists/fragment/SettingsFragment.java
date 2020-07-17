/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.cdot.lists.MainActivity;
import com.cdot.lists.R;
import com.cdot.lists.Settings;
import com.cdot.lists.databinding.SettingsFragmentBinding;

/**
 * This activity is invoked using startActivityForResult, and it will return a RESULT_OK if and only
 * if a new list store has been attached. Otherwise it will return RESULT_CANCELED
 */
public class SettingsFragment extends Fragment {

    SettingsFragmentBinding mBinding;

    private boolean mGeneral;

    SettingsFragment(boolean withGeneralSettings) {
        mGeneral = withGeneralSettings;
    }

    protected MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override // Fragment
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = SettingsFragmentBinding.inflate(getLayoutInflater());

        Settings settings = getMainActivity().getSettings();

        mBinding.greyOutChecked.setChecked(settings.getBool(Settings.greyChecked));
        mBinding.strikeChecked.setChecked(settings.getBool(Settings.strikeThroughChecked));
        mBinding.forceAlphaSort.setChecked(settings.getBool(Settings.forceAlphaSort));
        mBinding.showCheckedAtEnd.setChecked(settings.getBool(Settings.showCheckedAtEnd));
        mBinding.leftHandOperation.setChecked(settings.getBool(Settings.leftHandOperation));
        mBinding.autoDeleteChecked.setChecked(settings.getBool(Settings.autoDeleteChecked));
        mBinding.entireRowToggles.setChecked(settings.getBool(Settings.entireRowTogglesItem));

        mBinding.alwaysShow.setChecked(settings.getBool(Settings.alwaysShow));
        mBinding.openLatest.setChecked(settings.getBool(Settings.openLatest));
        mBinding.warnAboutDuplicates.setChecked(settings.getBool(Settings.warnAboutDuplicates));
        mBinding.textSizeSpinner.setSelection(settings.getInt(Settings.textSizeIndex));

        Uri uri = settings.getUri(Settings.backingStore);
        mBinding.backingStoreUri.setText(uri != null ? uri.toString() : getString(R.string.not_set));
        mBinding.generalSettings.setVisibility(mGeneral ? View.VISIBLE : View.GONE);

        mBinding.changeStore.setOnClickListener(this::changeStoreClicked);
        mBinding.createStore.setOnClickListener(this::createStoreClicked);

        mBinding.openLatest.setOnClickListener(v -> settings.setBool(Settings.openLatest, mBinding.openLatest.isChecked()));
        mBinding.forceAlphaSort.setOnClickListener(v -> settings.setBool(Settings.forceAlphaSort, mBinding.forceAlphaSort.isChecked()));
        mBinding.alwaysShow.setOnClickListener(v -> settings.setBool(Settings.alwaysShow, mBinding.alwaysShow.isChecked()));
        mBinding.warnAboutDuplicates.setOnClickListener(v -> settings.setBool(Settings.warnAboutDuplicates, mBinding.warnAboutDuplicates.isChecked()));
        mBinding.leftHandOperation.setOnClickListener(v -> settings.setBool(Settings.leftHandOperation, mBinding.leftHandOperation.isChecked()));
        mBinding.entireRowToggles.setOnClickListener(v -> settings.setBool(Settings.entireRowTogglesItem, mBinding.entireRowToggles.isChecked()));
        mBinding.autoDeleteChecked.setOnClickListener(v -> settings.setBool(Settings.autoDeleteChecked, mBinding.autoDeleteChecked.isChecked()));
        mBinding.showCheckedAtEnd.setOnClickListener(v -> settings.setBool(Settings.showCheckedAtEnd, mBinding.showCheckedAtEnd.isChecked()));
        mBinding.strikeChecked.setOnClickListener(v -> settings.setBool(Settings.strikeThroughChecked, mBinding.strikeChecked.isChecked()));
        mBinding.greyOutChecked.setOnClickListener(v -> settings.setBool(Settings.greyChecked, mBinding.greyOutChecked.isChecked()));

        mBinding.textSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override // AdapterView.OnItemSelectedListener
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

            @Override // AdapterView.OnItemSelectedListener
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
                if (adapterView == mBinding.textSizeSpinner)
                    settings.setInt(Settings.textSizeIndex, i);
            }
        });

        return mBinding.getRoot();
    }

    @Override // Fragment
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if ((requestCode == MainActivity.REQUEST_CHANGE_STORE || requestCode == MainActivity.REQUEST_CREATE_STORE) && resultCode == AppCompatActivity.RESULT_OK) {
            mBinding.backingStoreUri.setText(getMainActivity().getSettings().getUri(Settings.backingStore).toString());
            getMainActivity().onActivityResult(requestCode, resultCode, resultData);
        }
    }

    // Invoked from resource
    public void changeStoreClicked(View view) {
        Uri bs = getMainActivity().getSettings().getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, MainActivity.REQUEST_CHANGE_STORE);
    }

    // Invoked from resource
    public void createStoreClicked(View view) {
        Uri bs = getMainActivity().getSettings().getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, MainActivity.REQUEST_CREATE_STORE);
    }
}
