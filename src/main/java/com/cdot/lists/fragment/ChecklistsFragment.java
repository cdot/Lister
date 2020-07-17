/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.fragment;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.R;
import com.cdot.lists.databinding.ChecklistsFragmentBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.view.ChecklistsItemView;
import com.cdot.lists.view.EntryListItemView;

/**
 * Activity that displays a list of checklists. The checklists are stored in a paired Checklists
 * object.
 */
public class ChecklistsFragment extends EntryListFragment {
    private static final String TAG = "ChecklistsFragment";

    public ChecklistsFragment(Checklists lists) {
        mList = lists;
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
    }

    @Override // EntryListFragment
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ChecklistsFragmentBinding binding = ChecklistsFragmentBinding.inflate(inflater, container, false);
        setView(binding.itemListView, binding.checklistsFragment);
        notifyDataSetChanged();
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override // Fragment
    public void onCreateOptionsMenu(@NonNull Menu menu,  MenuInflater inflater) {
        // See https://stackoverflow.com/questions/15653737/oncreateoptionsmenu-inside-fragments
        // for MainActivity menu interaction
        Log.d(TAG, "onCreateOptionsMenu");
        inflater.inflate(R.menu.checklists, menu);
    }

    @Override // EntryListFragment
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistsItemView(item, true, this);
    }

    @Override // EntryListFragment
    protected String getHelpAsset() {
        return "Checklists";
    }

    @Override // Fragment
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (super.onOptionsItemSelected(menuItem))
            return true;

        switch (menuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(menuItem);

            case R.id.action_import_list:
                getMainActivity().importList();
                return true;

            case R.id.action_new_list:
                AlertDialog.Builder builder = new AlertDialog.Builder(getMainActivity());
                builder.setTitle(R.string.create_new_list);
                builder.setMessage(R.string.enter_name_of_new_list);
                final EditText editText = new EditText(getMainActivity());
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                editText.setSingleLine(true);
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    String listname = editText.getText().toString();
                    Checklist newList = new Checklist(mList, listname);
                    mList.add(newList);
                    mList.notifyListChanged();
                    getMainActivity().saveLists();
                    Log.d(TAG, "created list: " + newList.getText());
                    getMainActivity().pushFragment(new ChecklistFragment(newList));
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                editText.post(() -> {
                    editText.setFocusable(true);
                    editText.setFocusableInTouchMode(true);
                    editText.requestFocus();
                    ((InputMethodManager) getMainActivity().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                });
                return true;

            case R.id.action_settings:
                getMainActivity().pushFragment(new SettingsFragment(true));
                return true;
        }
    }
}
