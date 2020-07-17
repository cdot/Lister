/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.R;
import com.cdot.lists.fragment.ChecklistFragment;
import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;

/**
 * View of the title of a list
 */
@SuppressLint("ViewConstructor")
public class ChecklistsItemView extends EntryListItemView {
    private static final String TAG = "ChecklistsItemView";

    @SuppressLint("ClickableViewAccessibility")
    public ChecklistsItemView(EntryListItem item, boolean isMoving, EntryListFragment cxt) {
        super(item, isMoving, cxt, R.layout.checklists_item_view, R.menu.checklists_popup);

        if (!isMoving)
            addListeners();

        updateView();
    }

    @Override // View
    public void onClick(View view) {
        // A click on a list name will open that list in a ChecklistFragment
        getMainActivity().pushFragment(new ChecklistFragment((Checklist)mItem));
    }

    @Override // EntryListItemView
    protected boolean onAction(int act) {
        Checklists checklists = (Checklists) mItem.getContainer();
        AlertDialog.Builder builder;
        switch (act) {
            case R.id.action_delete:
                builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.confirm_delete);
                builder.setMessage(getContext().getString(R.string.confirm_delete_list, mItem.getText()));
                builder.setPositiveButton(R.string.ok, (dialogInterface, which_button) -> {
                    EntryList el = mItem.getContainer();
                    el.remove(mItem, true);
                    el.notifyListChanged();
                    getMainActivity().saveLists();
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;

            case R.id.action_rename:
                builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.rename_list);
                builder.setMessage(R.string.enter_new_name_of_list);
                final EditText editText = new EditText(getContext());
                editText.setSingleLine(true);
                editText.setText(mItem.getText());
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    mItem.setText(editText.getText().toString());
                    mItem.getContainer().notifyListChanged();
                    getMainActivity().saveLists();
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;

            case R.id.action_copy:
                checklists.copyList(mItem);
                getMainActivity().saveLists();
                return true;
                
            default:
                return false;
        }
    }
}
