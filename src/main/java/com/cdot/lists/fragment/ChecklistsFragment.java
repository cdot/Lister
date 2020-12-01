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
import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.R;
import com.cdot.lists.databinding.ChecklistsFragmentBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.view.ChecklistsItemView;
import com.cdot.lists.view.EntryListItemView;

/**
 * Fragment that displays a list of checklists. The checklists are stored in the MainActivity.
 */
public class ChecklistsFragment extends EntryListFragment {
    private static final String TAG = "ChecklistsFragment";

    private ChecklistsFragmentBinding mBinding;

    /**
     * Construct a fragment to manage interaction with the given checklists.
     * @param lists the Checklists being managed
     */
    public ChecklistsFragment(Checklists lists) {
        mList = lists;
        setHasOptionsMenu(true);
    }

    @Override // Fragment
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = ChecklistsFragmentBinding.inflate(inflater, container, false);
        setView(mBinding.itemListView, mBinding.checklistsFragment);
        return mBinding.getRoot();
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

    @Override // EntryListFragment
    public void onListChanged(EntryListItem item) {
        super.onListChanged(item);
        mBinding.listsMessage.setVisibility(mList.size() == 0 ? View.VISIBLE : View.GONE);
        mBinding.itemListView.setVisibility(mList.size() == 0 ? View.GONE : View.VISIBLE);
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
                    Log.d(TAG, "created list: " + newList.getText());
                    mList.notifyChangeListeners();
                    getMainActivity().save();
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
