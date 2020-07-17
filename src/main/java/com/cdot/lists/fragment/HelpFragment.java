/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cdot.lists.R;
import com.cdot.lists.databinding.HelpFragmentBinding;

/**
 * Simple activity to view help information stored in an html file
 */
public class HelpFragment extends Fragment {
    String mPage;

    public HelpFragment(String page) {
        mPage = page;
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        HelpFragmentBinding binding = HelpFragmentBinding.inflate(inflater, container, false);
        binding.webview.getSettings().setBuiltInZoomControls(true);
        binding.webview.loadUrl("file:///android_asset/html/" + getString(R.string.locale_prefix) + "/" + mPage + ".html");
        return binding.getRoot();
    }
}
