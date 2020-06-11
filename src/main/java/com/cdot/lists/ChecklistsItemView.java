package com.cdot.lists;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.cdot.lists.databinding.ChecklistsItemViewBinding;

/**
 * View of the title of a list
 */
public class ChecklistsItemView extends EntryListItemView {
    private static final String TAG = "ChecklistsItemView";

    private ChecklistsItemViewBinding mBinding;

    @SuppressLint("ClickableViewAccessibility")
    ChecklistsItemView(EntryListItem item, boolean isMoving, Context cxt) {
        super(item, isMoving, cxt, R.layout.checklists_item_view, R.menu.checklists_popup);

        if (!isMoving)
            addListeners();

        updateView();
    }

    public void onClick(View view) {
        Intent intent = new Intent(getContext(), ChecklistActivity.class);
        int idx = mItem.getContainer().indexOf(mItem);
        intent.putExtra("index", idx);
        intent.putExtra("name", mItem.getText());
        getContext().startActivity(intent);
    }

    @Override // EntryListItemView
    protected boolean onAction(int act) {
        Checklists checklists = (Checklists)mItem.getContainer();
        int index = mItem.getContainer().indexOf(mItem);
        switch (act) {
            case R.id.action_delete:
                showDeleteConfirmDialog();
                return true;
            case R.id.action_rename:
                showRenameDialog(R.string.action_rename_list, R.string.enter_new_name_of_list);
                return true;
            case R.id.action_copy:
                checklists.cloneListAt(index);
                mItem.getContainer().notifyListChanged();
                return true;
            default:
                return false;
        }
    }
}
