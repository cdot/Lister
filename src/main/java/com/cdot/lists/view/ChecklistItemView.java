/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view;

import android.annotation.SuppressLint;
import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.EntryListActivity;
import com.cdot.lists.Lister;
import com.cdot.lists.R;
import com.cdot.lists.databinding.ChecklistItemViewBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.ChecklistItem;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;

import static com.cdot.lists.model.ChecklistItem.isDone;

/**
 * View for a single item in a checklist, and for moving same.
 */
@SuppressLint("ViewConstructor")
public class ChecklistItemView extends EntryListItemView {
    private static final String TAG = ChecklistItemView.class.getSimpleName();

    private static final float TRANSPARENCY_OPAQUE = 1;
    private static final float TRANSPARENCY_GREYED = 0.5f;
    private static final float TRANSPARENCY_FAINT = 0.2f;

    // True if the checkbox is on the right (which is where the basic layout has it)
    private boolean mCheckboxOnRight;

    private final ChecklistItemViewBinding mBinding;

    // True if this view is of an item being moved
    private final boolean mIsMoving;

    /**
     * @param item     the item being viewed
     * @param isMoving true if this is to be used as a view for dragging an item to a new position, false for an item in a fixed list
     * @param cxt      fragment
     */
    public ChecklistItemView(EntryListItem item, boolean isMoving, EntryListActivity cxt) {
        super(item, cxt);
        mIsMoving = isMoving;
        mBinding = ChecklistItemViewBinding.inflate(LayoutInflater.from(cxt), this, true);
        if (!isMoving) {
            addItemListeners(mBinding.moveButton, R.menu.checklist_item_popup);
            mBinding.checklistCheckbox.setOnClickListener(view -> {
                if (setChecked(mBinding.checklistCheckbox.isChecked())) {
                    Log.d(TAG, "item checked");
                    if (mBinding.checklistCheckbox.isChecked())
                        mItem.setFlag(isDone);
                    else
                        mItem.clearFlag(isDone);
                    checkpoint();
                }
            });
        }
        mCheckboxOnRight = true;
        updateView();
    }

    @Override // View.OnClickListener()
    public void onClick(View view) {
        if (!mIsMoving && getLister().getBool(Lister.PREF_ENTIRE_ROW_TOGGLES)) {
            CheckBox cb = mBinding.checklistCheckbox;
            if (setChecked(!cb.isChecked())) {
                Log.d(TAG, "Item toggled");
                checkpoint();
            }
        }
    }

    @Override // EntryListItemView
    protected void setTextFormatting(TextView it) {
        super.setTextFormatting(it);

        // Transparency
        float f = TRANSPARENCY_OPAQUE; // Completely opague

        if (mItem.getFlag(ChecklistItem.isDone) && getLister().getBool(Lister.PREF_GREY_CHECKED))
            // Greyed out
            f = TRANSPARENCY_GREYED;
        else if (!mIsMoving && mItem == mActivity.mMovingItem) {
            // Item being moved (but NOT the moving view)
            f = TRANSPARENCY_FAINT;
            mBinding.rightLayout.setAlpha(f);
            mBinding.leftLayout.setAlpha(f);
        }

        it.setAlpha(f);

        // Strike through
        if (!mItem.getFlag(ChecklistItem.isDone) || !getLister().getBool(Lister.PREF_STRIKE_CHECKED))
            it.setPaintFlags(it.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        else
            it.setPaintFlags(it.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    }

    @Override // EntryListItemView
    public void updateView() {
        super.updateView();

        ViewGroup left = mBinding.leftLayout;
        ViewGroup right = mBinding.rightLayout;
        CheckBox checkBox = mBinding.checklistCheckbox;
        ImageButton moveButton = mBinding.moveButton;

        if (getLister().getBool(Lister.PREF_LEFT_HANDED) && mCheckboxOnRight) {
            // Move checkbox to left panel
            right.removeView(checkBox);
            left.removeView(moveButton);
            left.addView(checkBox);
            right.addView(moveButton);
            mCheckboxOnRight = false;
        } else if (!getLister().getBool(Lister.PREF_LEFT_HANDED) && !mCheckboxOnRight) {
            // Move checkbox to right panel
            left.removeView(checkBox);
            right.removeView(moveButton);
            right.addView(checkBox);
            left.addView(moveButton);
            mCheckboxOnRight = true;
        }
        checkBox.setChecked(mItem.getFlag(ChecklistItem.isDone));
    }

    @Override // EntryListItemView
    protected boolean onPopupMenuAction(int act) {
        if (act == R.id.action_delete) {
            EntryList list = mItem.getParent();
            list.newUndoSet();
            list.remove(mItem, true);
            Log.d(TAG, "item deleted");
            list.notifyChangeListeners();
            checkpoint();

        } else if (act == R.id.action_rename) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.edit_list_item);
            final EditText editText = new EditText(getContext());
            editText.setSingleLine(true);
            editText.setText(mItem.getText());
            builder.setView(editText);
            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                mItem.setText(editText.getText().toString());
                Log.d(TAG, "item renamed");
                mItem.notifyChangeListeners();
                checkpoint();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();

        } else
            return false;

        return true;
    }

    /**
     * Handle checking / unchecking a single item. Notifies change listeners.
     *
     * @param isChecked the check status
     */
    private boolean setChecked(boolean isChecked) {
        Checklist list = (Checklist) mItem.getParent();
        if (list.getFlag(Checklist.autoDeleteChecked) && isChecked) {
            EntryList el = mItem.getParent();
            el.newUndoSet();
            el.remove(mItem, true);
            el.notifyChangeListeners();
            return true;
        }
        mBinding.checklistCheckbox.setChecked(isChecked);
        if (mItem.getFlag(ChecklistItem.isDone) != isChecked) {
            if (isChecked)
                mItem.setFlag(ChecklistItem.isDone);
            else
                mItem.clearFlag(ChecklistItem.isDone);
            mItem.notifyChangeListeners();
            return true;
        }
        return false;
    }
}
