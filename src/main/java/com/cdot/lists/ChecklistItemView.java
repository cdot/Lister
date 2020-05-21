package com.cdot.lists;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.cdot.lists.databinding.ChecklistItemViewBinding;

/**
 * View for a single item in a checklist, and for moving same.
 */
class ChecklistItemView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener, View.OnTouchListener {
    private static final String TAG = "ChecklistItemView";
    private Checklist.ChecklistItem mItem;
    private boolean mIsMoving = false;
    private int mVerticalPadding = 0;
    private Context mContext;
    private ChecklistItemViewBinding mBinding;
    private boolean mControlsOnRight = true;

    /**
     * @param item     the item being viewed
     * @param isMoving true if this is to be used as a view for dragging an item to a new position, false for an item in a fixed list
     * @param cxt      activity
     */
    ChecklistItemView(Checklist.ChecklistItem item, boolean isMoving, Context cxt) {
        super(cxt);

        mBinding = ChecklistItemViewBinding.inflate((LayoutInflater) cxt.getSystemService(Context.LAYOUT_INFLATER_SERVICE));

        mIsMoving = isMoving;
        mContext = cxt;
        mItem = item;

        addView(mBinding.rowLayout);

        if (!mIsMoving) {
            setOnClickListener(this);
            setOnLongClickListener(this);
            mBinding.checkbox.setOnClickListener(this);
            mBinding.moveButton.setOnTouchListener(this);
        }

        updateView();
    }

    void setItem(Checklist.ChecklistItem item) {
        mItem = item;
    }

    Checklist.ChecklistItem getItem() {
        return mItem;
    }

    private void setTextFormatting() {
        // Size
        switch (Settings.getInt("textSizeIndex")) {
            case Settings.TEXT_SIZE_SMALL:
                mBinding.itemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                mVerticalPadding = 0;
                break;
            case Settings.TEXT_SIZE_MEDIUM:
            case Settings.TEXT_SIZE_DEFAULT:
                mBinding.itemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                mVerticalPadding = 5;
                break;
            case Settings.TEXT_SIZE_LARGE:
                mBinding.itemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                mVerticalPadding = 10;
                break;
        }
        mBinding.itemText.setPadding(0, mVerticalPadding, 0, mVerticalPadding);

        // Transparency
        float f = 1; // Completely opague

        if (mItem.mDone && Settings.getBool(Settings.greyCheckedItems))
            // Greyed out
            f = 0.5f;
        else if (!mIsMoving && mItem == mItem.getChecklist().mMovingItem)
            // Moving item
            f = 0.2f;

        mBinding.itemText.setAlpha(f);
        mBinding.rightLayout.setAlpha(f);
        mBinding.leftLayout.setAlpha(f);

        // Strike through
        if (!mItem.mDone || !Settings.getBool(Settings.strikeThroughCheckedItems))
            mBinding.itemText.setPaintFlags(mBinding.itemText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        else
            mBinding.itemText.setPaintFlags(mBinding.itemText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    }

    public void updateView() {
        // Update the item text
        mBinding.itemText.setText(mItem.getText());
        setTextFormatting();

        if (Settings.getBool(Settings.checkBoxOnLeftSide) && mControlsOnRight) {
            // Move controls to left panel
            mBinding.rightLayout.removeAllViews();
            mBinding.leftLayout.addView(mBinding.moveButton);
            mBinding.leftLayout.addView(mBinding.checkbox);
            mControlsOnRight = false;
        } else if (!Settings.getBool(Settings.checkBoxOnLeftSide) && !mControlsOnRight) {
            // Move controls to right panel
            mBinding.leftLayout.removeAllViews();
            mBinding.rightLayout.addView(mBinding.checkbox);
            mBinding.rightLayout.addView(mBinding.moveButton);
            mControlsOnRight = true;
        }

        mBinding.checkbox.setChecked(mItem.mDone);
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        Log.d(TAG, "OnTouch " + motionEvent.getAction() + " " + Integer.toHexString(System.identityHashCode(this)));
        if (view != mBinding.moveButton)
            return false;
        if (motionEvent.getAction() != 0) {
            return true;
        }
        Log.d(TAG, "onTouch moveButton " + mBinding.itemText.getText());
        mItem.getChecklist().setMovingItem(mItem);
        return true;
    }

    /**
     * Handle all the clicks on the controls in an item row
     */
    public void onClick(View view) {
        Log.d(TAG, "OnClick");
        if (view == mBinding.checkbox)
            setChecked(mBinding.checkbox.isChecked());
        else if (view == this && Settings.getBool(Settings.entireRowTogglesItem))
            setChecked(!mBinding.checkbox.isChecked());
    }

    public boolean onLongClick(View view) {
        if (view == this) {
            showMenu();
            return true;
        }
        return false;
    }

    private void showMenu() {
        PopupMenu popupMenu = new PopupMenu(mContext, this);
        popupMenu.inflate(R.menu.checklist_item_popup);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.action_delete) {
                    mItem.getChecklist().remove(mItem);
                    return true;
                } else if (itemId == R.id.action_rename) {
                    showRenameItemDialog();
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    public void setChecked(boolean isChecked) {
        if (Settings.getBool(Settings.autoDeleteCheckedItems) && isChecked)
            mItem.getChecklist().remove(mItem);
        else {
            mBinding.checkbox.setChecked(isChecked);
            mItem.mDone = isChecked;
        }
        // Update the list!
        // TODO: is this required?
        mItem.getChecklist().notifyItemChanged();
    }

    private void showRenameItemDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.edit_list_item);
        final EditText editText = new EditText(mContext);
        builder.setView(editText);
        editText.setText(mBinding.itemText.getText());
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                renameItem(editText.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void renameItem(String str) {
        mItem.setText(str);
    }
}