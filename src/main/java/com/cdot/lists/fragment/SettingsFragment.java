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

        mBinding.greyOutChecked.setChecked(Settings.getBool(Settings.greyChecked));
        mBinding.strikeChecked.setChecked(Settings.getBool(Settings.strikeThroughChecked));
        mBinding.forceAlphaSort.setChecked(Settings.getBool(Settings.forceAlphaSort));
        mBinding.showCheckedAtEnd.setChecked(Settings.getBool(Settings.showCheckedAtEnd));
        mBinding.leftHandOperation.setChecked(Settings.getBool(Settings.leftHandOperation));
        mBinding.autoDeleteChecked.setChecked(Settings.getBool(Settings.autoDeleteChecked));
        mBinding.entireRowToggles.setChecked(Settings.getBool(Settings.entireRowTogglesItem));

        mBinding.alwaysShow.setChecked(Settings.getBool(Settings.alwaysShow));
        mBinding.warnAboutDuplicates.setChecked(Settings.getBool(Settings.warnAboutDuplicates));
        mBinding.textSizeSpinner.setSelection(Settings.getInt(Settings.textSizeIndex));

        Uri uri = Settings.getUri(Settings.backingStore);
        mBinding.backingStoreUri.setText(uri != null ? uri.toString() : getString(R.string.not_set));
        mBinding.generalSettings.setVisibility(mGeneral ? View.VISIBLE : View.GONE);

        mBinding.changeStore.setOnClickListener(this::changeStoreClicked);
        mBinding.createStore.setOnClickListener(this::createStoreClicked);

        mBinding.forceAlphaSort.setOnClickListener(v -> Settings.setBool(Settings.forceAlphaSort, mBinding.forceAlphaSort.isChecked()));
        mBinding.alwaysShow.setOnClickListener(v -> Settings.setBool(Settings.alwaysShow, mBinding.alwaysShow.isChecked()));
        mBinding.warnAboutDuplicates.setOnClickListener(v -> Settings.setBool(Settings.warnAboutDuplicates, mBinding.warnAboutDuplicates.isChecked()));
        mBinding.leftHandOperation.setOnClickListener(v -> Settings.setBool(Settings.leftHandOperation, mBinding.leftHandOperation.isChecked()));
        mBinding.entireRowToggles.setOnClickListener(v -> Settings.setBool(Settings.entireRowTogglesItem, mBinding.entireRowToggles.isChecked()));
        mBinding.autoDeleteChecked.setOnClickListener(v -> Settings.setBool(Settings.autoDeleteChecked, mBinding.autoDeleteChecked.isChecked()));
        mBinding.showCheckedAtEnd.setOnClickListener(v -> Settings.setBool(Settings.showCheckedAtEnd, mBinding.showCheckedAtEnd.isChecked()));
        mBinding.strikeChecked.setOnClickListener(v -> Settings.setBool(Settings.strikeThroughChecked, mBinding.strikeChecked.isChecked()));
        mBinding.greyOutChecked.setOnClickListener(v -> Settings.setBool(Settings.greyChecked, mBinding.greyOutChecked.isChecked()));

        mBinding.textSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override // AdapterView.OnItemSelectedListener
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

            @Override // AdapterView.OnItemSelectedListener
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
                if (adapterView == mBinding.textSizeSpinner)
                    Settings.setInt(Settings.textSizeIndex, i);
            }
        });

        return mBinding.getRoot();
    }

    @Override // Fragment
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if ((requestCode == MainActivity.REQUEST_CHANGE_STORE || requestCode == MainActivity.REQUEST_CREATE_STORE) && resultCode == AppCompatActivity.RESULT_OK) {
            mBinding.backingStoreUri.setText(Settings.getUri(Settings.backingStore).toString());
            getMainActivity().onActivityResult(requestCode, resultCode, resultData);
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
        startActivityForResult(intent, MainActivity.REQUEST_CHANGE_STORE);
    }

    // Invoked from resource
    public void createStoreClicked(View view) {
        Uri bs = Settings.getUri(Settings.backingStore);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (bs != null && Build.VERSION.SDK_INT >= 26)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, bs);
        intent.setType("application/json");
        startActivityForResult(intent, MainActivity.REQUEST_CREATE_STORE);
    }
}
