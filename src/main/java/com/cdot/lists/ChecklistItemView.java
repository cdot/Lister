package com.cdot.lists;

import android.annotation.SuppressLint;
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
class ChecklistItemView extends LinearLayout {
    private static final String TAG = "ChecklistItemView";
    private Checklist.ChecklistItem mItem;
    private boolean mIsMoving;
    private Context mContext;
    private ChecklistItemViewBinding mBinding;
    private boolean mControlsOnRight;

    /**
     * @param item     the item being viewed
     * @param isMoving true if this is to be used as a view for dragging an item to a new position, false for an item in a fixed list
     * @param cxt      activity
     */
    @SuppressLint("ClickableViewAccessibility")
    ChecklistItemView(Checklist.ChecklistItem item, boolean isMoving, Context cxt) {
        super(cxt);

        mBinding = ChecklistItemViewBinding.inflate((LayoutInflater) cxt.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        mControlsOnRight = true;
        mIsMoving = isMoving;
        mContext = cxt;
        mItem = item;

        addView(mBinding.rowLayout);

        if (!mIsMoving) {
            setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Log.d(TAG, "OnClick");
                    if (Settings.getBool(Settings.entireRowTogglesItem))
                        setChecked(!mBinding.checkbox.isChecked());
                }
            });
            setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    showMenu();
                    return true;
                }
            });
            mBinding.checkbox.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Log.d(TAG, "OnClick");
                    setChecked(mBinding.checkbox.isChecked());
                }
            });
            mBinding.moveButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    Log.d(TAG, "OnTouch " + motionEvent.getAction() + " " + Integer.toHexString(System.identityHashCode(this)));
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        mItem.getChecklist().setMovingItem(mItem);
                    }
                    return true;
                }
            });
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
        int padding;
        // Size
        switch (Settings.getInt("textSizeIndex")) {
            case Settings.TEXT_SIZE_SMALL:
                mBinding.itemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                padding = 0;
                break;
            default:
                mBinding.itemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                padding = 5;
                break;
            case Settings.TEXT_SIZE_LARGE:
                mBinding.itemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                padding = 10;
                break;
        }
        mBinding.itemText.setPadding(0, padding, 0, padding);

        // Transparency
        float f = 1; // Completely opague

        if (mItem.isDone() && Settings.getBool(Settings.greyCheckedItems))
            // Greyed out
            f = 0.5f;
        else if (!mIsMoving && mItem == mItem.getChecklist().mMovingItem)
            // Moving item
            f = 0.2f;

        mBinding.itemText.setAlpha(f);
        mBinding.rightLayout.setAlpha(f);
        mBinding.leftLayout.setAlpha(f);

        // Strike through
        if (!mItem.isDone() || !Settings.getBool(Settings.strikeThroughCheckedItems))
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

        mBinding.checkbox.setChecked(mItem.isDone());
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
            mItem.setDone(isChecked);
        }
        // Update the list!
        mItem.getChecklist().notifyListChanged();
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
        mItem.getChecklist().notifyListChanged();
    }
}
