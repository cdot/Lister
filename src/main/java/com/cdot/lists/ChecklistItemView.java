/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Context;
import android.graphics.Paint;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * View for a single item in a checklist, and for moving same.
 */
class ChecklistItemView extends EntryListItemView {
    private static final String TAG = "ChecklistItemView";

    // True if the checkbox is on the right (which is where the basic layout has it)
    private boolean mCheckboxOnRight;

    /**
     * @param item     the item being viewed
     * @param isMoving true if this is to be used as a view for dragging an item to a new position, false for an item in a fixed list
     * @param cxt      activity
     */
    ChecklistItemView(EntryListItem item, boolean isMoving, Context cxt) {
        super(item, isMoving, cxt, R.layout.checklist_item_view, R.menu.checklist_item_popup);
        mCheckboxOnRight = true;
        updateView();
    }

    @Override // View.OnClickListener()
    public void onClick(View view) {
        if (!mIsMoving && Settings.getBool(Settings.entireRowTogglesItem)) {
            CheckBox cb = findViewById(R.id.checklist_checkbox);
            setChecked(!cb.isChecked());
        }
    }

    @Override // EntryListItemView
    void addListeners() {
        super.addListeners();
        final CheckBox cb = findViewById(R.id.checklist_checkbox);
        cb.setOnClickListener(view -> setChecked(cb.isChecked()));
    }

    @Override // EntryListItemView
    protected void setTextFormatting() {
        super.setTextFormatting();

        // Transparency
        float f = Settings.TRANSPARENCY_OPAQUE; // Completely opague

        if (((ChecklistItem) mItem).isDone() && Settings.getBool(Settings.greyChecked))
            // Greyed out
            f = Settings.TRANSPARENCY_GREYED;
        else if (!mIsMoving && mItem == mItem.getContainer().mMovingItem)
            // Item being moved (but NOT the moving view)
            f = Settings.TRANSPARENCY_FAINT;

        TextView it = findViewById(R.id.item_text);
        it.setAlpha(f);
        findViewById(R.id.right_layout).setAlpha(f);
        findViewById(R.id.left_layout).setAlpha(f);

        // Strike through
        if (!((ChecklistItem) mItem).isDone() || !Settings.getBool(Settings.strikeThroughChecked))
            it.setPaintFlags(it.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        else
            it.setPaintFlags(it.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    }

    @Override // EntryListItemView
    public void updateView() {
        super.updateView();

        LinearLayout left = findViewById(R.id.left_layout);
        LinearLayout right = findViewById(R.id.right_layout);
        CheckBox checkBox = findViewById(R.id.checklist_checkbox);
        ImageButton moveButton = findViewById(R.id.move_button);

        if (Settings.getBool(Settings.leftHandOperation) && mCheckboxOnRight) {
            // Move checkbox to left panel
            right.removeView(checkBox);
            left.removeView(moveButton);
            left.addView(checkBox);
            right.addView(moveButton);
            mCheckboxOnRight = false;
        } else if (!Settings.getBool(Settings.leftHandOperation) && !mCheckboxOnRight) {
            // Move checkbox to right panel
            left.removeView(checkBox);
            right.removeView(moveButton);
            right.addView(checkBox);
            left.addView(moveButton);
            mCheckboxOnRight = true;
        }
        moveButton.setVisibility((mItem.getContainer().mShowSorted ||
                ((ChecklistItem)mItem).mDone && Settings.getBool(Settings.showCheckedAtEnd)) ? View.GONE : View.VISIBLE);
    checkBox.setChecked(((ChecklistItem) mItem).isDone());
    }

    /**
     * Handle checking / unchecking a single item
     * @param isChecked the check status
     */
    private void setChecked(boolean isChecked) {
        if (Settings.getBool(Settings.autoDeleteChecked) && isChecked) {
            EntryList el = mItem.getContainer();
            el.newUndoSet();
            el.remove(mItem, true);
        } else {
            CheckBox cb = findViewById(R.id.checklist_checkbox);
            cb.setChecked(isChecked);
            ((ChecklistItem) mItem).setDone(isChecked);
        }
        // Update the list!
        mItem.notifyListChanged(true);
    }

    @Override // EntryListItemView
    protected boolean onAction(int act) {
        switch (act) {
            case R.id.action_delete:
                EntryList list = mItem.getContainer();
                list.newUndoSet();
                list.remove(mItem, true);
                list.notifyListChanged(true);
                return true;

            case R.id.action_rename:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.edit_list_item);
                final EditText editText = new EditText(getContext());
                editText.setSingleLine(true);
                editText.setText(mItem.getText());
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    mItem.setText(editText.getText().toString());
                    mItem.notifyListChanged(true);
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;

            default:
                return false;
        }
    }
}
