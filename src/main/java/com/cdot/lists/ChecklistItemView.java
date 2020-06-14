/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Context;

import android.content.DialogInterface;
import android.graphics.Paint;
import android.util.Log;
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
    private boolean mControlsOnRight;

    /**
     * @param item     the item being viewed
     * @param isMoving true if this is to be used as a view for dragging an item to a new position, false for an item in a fixed list
     * @param cxt      activity
     */
    ChecklistItemView(EntryListItem item, boolean isMoving, Context cxt) {
        super(item, isMoving, cxt, R.layout.checklist_item_view, R.menu.checklist_item_popup);
        mControlsOnRight = true;

        if (!isMoving) {
            addListeners();
            final CheckBox cb = findViewById(R.id.checklist_checkbox);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    setChecked(cb.isChecked());
                }
            });
        }
        updateView();
    }

    @Override // View.OnClickListener()
    public void onClick(View view) {
        if (Settings.getBool(Settings.entireRowTogglesItem)) {
            CheckBox cb = findViewById(R.id.checklist_checkbox);
            setChecked(!cb.isChecked());
        }
    }

    @Override // EntryListItemView
    protected void setTextFormatting() {
        super.setTextFormatting();

        // Transparency
        float f = 1; // Completely opague

        if (((ChecklistItem) mItem).isDone() && Settings.getBool(Settings.greyChecked))
            // Greyed out
            f = 0.5f;
        else if (!mIsMoving && mItem == mItem.getContainer().mMovingItem)
            // Moving item
            f = 0.2f;

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

        LinearLayout ll = findViewById(R.id.left_layout);
        LinearLayout rl = findViewById(R.id.right_layout);
        CheckBox cb = findViewById(R.id.checklist_checkbox);
        ImageButton mb = findViewById(R.id.move_button);
        if (Settings.getBool(Settings.forceAlphaSort)) {
            ll.removeView(mb);
            rl.removeView(mb);
        }

        if (Settings.getBool(Settings.leftHandOperation) && mControlsOnRight) {
            // Move checkbox to left panel
            rl.removeView(cb);
            ll.addView(cb);
            //ll.removeView(mb);
            if (!Settings.getBool(Settings.forceAlphaSort))
                rl.addView(mb);
            mControlsOnRight = false;
        } else if (!Settings.getBool(Settings.leftHandOperation) && !mControlsOnRight) {
            // Move checkbox to right panel
            ll.removeView(cb);
            rl.addView(cb);
            //rl.removeView(mb);
            if (!Settings.getBool(Settings.forceAlphaSort))
                ll.addView(mb);
            mControlsOnRight = true;
        }
        cb.setChecked(((ChecklistItem) mItem).isDone());
    }

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
                return true;

            case R.id.action_rename:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.edit_list_item);
                final EditText editText = new EditText(getContext());
                editText.setSingleLine(true);
                editText.setText(mItem.getText());
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mItem.setText(editText.getText().toString());
                        mItem.notifyListChanged(true);
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;

            default:
                return false;
        }
    }
}
