/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.ChecklistActivity;
import com.cdot.lists.EntryListActivity;
import com.cdot.lists.ListerActivity;
import com.cdot.lists.R;
import com.cdot.lists.databinding.ChecklistsItemViewBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;

/**
 * View of the title of a list
 */
@SuppressLint("ViewConstructor")
public class ChecklistsItemView extends EntryListItemView {
    private static final String TAG = ChecklistsItemView.class.getSimpleName();
    ChecklistsItemViewBinding mBinding;

    @SuppressLint("ClickableViewAccessibility")
    public ChecklistsItemView(EntryListItem item, boolean isMoving, EntryListActivity cxt) {
        super(item, cxt);
        mBinding = ChecklistsItemViewBinding.inflate(LayoutInflater.from(cxt), this, true);

        if (!isMoving)
            addItemListeners(mBinding.moveButton, R.menu.checklists_popup);

        updateView();
    }

    @Override // View
    public void onClick(View view) {
        // A click on a list name will open that list in a ChecklistActivity
        Intent intent = new Intent(getContext(), ChecklistActivity.class);
        intent.putExtra(ListerActivity.UID_EXTRA, mItem.getSessionUID());
        getContext().startActivity(intent);
    }

    @Override // EntryListItemView
    protected boolean onPopupMenuAction(int act) {
        Checklists checklists = (Checklists) mItem.getParent();
        AlertDialog.Builder builder;
        if (act == R.id.action_delete) {
            builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.confirm_delete);
            builder.setMessage(getContext().getString(R.string.confirm_delete_list, mItem.getText()));
            builder.setPositiveButton(R.string.ok, (dialogInterface, which_button) -> {
                EntryList el = mItem.getParent();
                el.remove(mItem, true);
                Log.d(TAG, "list deleted");
                el.notifyChangeListeners();
                checkpoint();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();

        } else if (act == R.id.action_rename) {
            builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.rename_list);
            builder.setMessage(R.string.enter_new_name_of_list);
            final EditText editText = new EditText(getContext());
            editText.setSingleLine(true);
            editText.setText(mItem.getText());
            builder.setView(editText);
            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                mItem.setText(editText.getText().toString());
                Log.d(TAG, "list renamed");
                mItem.getParent().notifyChangeListeners();
                checkpoint();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();

        } else if (act == R.id.action_copy) {
            Checklist checklist = new Checklist((Checklist)mItem);
            String newname = checklist.getText() + " (copy)";
            checklist.setText(newname);
            checklists.addChild(checklist);
            checklists.notifyChangeListeners();
            Log.d(TAG, "list copied");
            checkpoint();

        } else
            return false;

        return true;
    }
}
