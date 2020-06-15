package com.cdot.lists;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

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
        Checklists checklists = (Checklists) mItem.getContainer();
        int index = mItem.getContainer().indexOf(mItem);
        AlertDialog.Builder builder;
        switch (act) {
            case R.id.action_delete:
                builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.confirm_delete);
                builder.setMessage(getContext().getString(R.string.confirm_delete_list, mItem.getText()));
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which_button) {
                        EntryList el = mItem.getContainer();
                        el.remove(mItem, true);
                        el.notifyListChanged(true);
                    }
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
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mItem.setText(editText.getText().toString());
                        mItem.getContainer().notifyListChanged(true);
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;

            case R.id.action_copy:
                checklists.cloneList(mItem, getContext());
                return true;
                
            default:
                return false;
        }
    }
}
