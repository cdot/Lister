/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view;

import android.annotation.SuppressLint;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.R;
import com.cdot.lists.Settings;
import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.ChecklistItem;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;

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

    /**
     * @param item     the item being viewed
     * @param isMoving true if this is to be used as a view for dragging an item to a new position, false for an item in a fixed list
     * @param cxt      fragment
     */
    public ChecklistItemView(EntryListItem item, boolean isMoving, EntryListFragment cxt) {
        super(item, isMoving, cxt, R.layout.checklist_item_view, R.menu.checklist_item_popup);
        mCheckboxOnRight = true;
        updateView();
    }

    @Override // View.OnClickListener()
    public void onClick(View view) {
        if (!mIsMoving && Settings.getBool(Settings.entireRowToggles)) {
            CheckBox cb = findViewById(R.id.checklist_checkbox);
            if (setChecked(!cb.isChecked())) {
                Log.d(TAG, "Item toggled");
                getMainActivity().save();
            }
        }
    }

    @Override
        // EntryListItemView
    void addListeners() {
        super.addListeners();
        final CheckBox cb = findViewById(R.id.checklist_checkbox);
        cb.setOnClickListener(view -> {
            if (setChecked(cb.isChecked())) {
                Log.d(TAG, "item checked");
                getMainActivity().save();
            }
        });
    }

    @Override // EntryListItemView
    protected void setTextFormatting() {
        super.setTextFormatting();

        // Transparency
        float f = TRANSPARENCY_OPAQUE; // Completely opague

        if (((ChecklistItem) mItem).isDone() && Settings.getBool(Settings.dimChecked))
            // Greyed out
            f = TRANSPARENCY_GREYED;
        else if (!mIsMoving && mItem == getFragment().mMovingItem)
            // Item being moved (but NOT the moving view)
            f = TRANSPARENCY_FAINT;

        TextView it = findViewById(R.id.item_text);
        it.setAlpha(f);
        findViewById(R.id.right_layout).setAlpha(f);
        findViewById(R.id.left_layout).setAlpha(f);

        // Strike through
        if (!((ChecklistItem) mItem).isDone() || !Settings.getBool(Settings.strikeChecked))
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
        checkBox.setChecked(((ChecklistItem) mItem).isDone());
    }

    @Override // EntryListItemView
    protected boolean onAction(int act) {
        if (act == R.id.action_delete) {
            EntryList list = mItem.getContainer();
            list.newUndoSet();
            list.remove(mItem, true);
            Log.d(TAG, "item deleted");
            list.notifyChangeListeners();
            getMainActivity().save();

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
                getMainActivity().save();
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
        Checklist list = (Checklist) mItem.getContainer();
        if (list.autoDeleteChecked && isChecked) {
            EntryList el = mItem.getContainer();
            el.newUndoSet();
            el.remove(mItem, true);
            el.notifyChangeListeners();
            return true;
        }
        CheckBox cb = findViewById(R.id.checklist_checkbox);
        cb.setChecked(isChecked);
        if (((ChecklistItem) mItem).setDone(isChecked)) {
            mItem.notifyChangeListeners();
            return true;
        }
        return false;
    }
}
