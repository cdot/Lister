package com.cdot.lists;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

class ChecklistItemView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener, View.OnTouchListener {
    private static final String TAG = "ChecklistItemView";
    private CheckBox mCheckBox;
    private ImageButton mDeleteButton;
    private ImageButton mDownButton;
    private Checklist.ChecklistItem mItem;
    private LinearLayout mItemOptionsRow;
    private LinearLayout mItemOptionsRowLeft;
    private TextView mItemText;
    private ImageButton mMoveButton;
    private boolean mMovingViewPreview = false;
    private ImageButton mUpButton;
    private int mVerticalPadding = 0;
    private Context mContext;

    ChecklistItemView(Checklist.ChecklistItem item, Context cxt) {
        super(cxt);
        init(item, cxt);
    }

    ChecklistItemView(Checklist.ChecklistItem item, boolean previewing, Context cxt) {
        super(cxt);
        mMovingViewPreview = previewing;
        init(item, cxt);
    }

    private void init(Checklist.ChecklistItem item, Context cxt) {
        mContext = cxt;
        mItem = item;

        mItemText = new TextView(cxt);
        switch (Settings.getInt("textSizeIndex")) {
            case Settings.TEXT_SIZE_SMALL:
                mItemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                mVerticalPadding = 0;
                break;
            case Settings.TEXT_SIZE_MEDIUM:
            case Settings.TEXT_SIZE_DEFAULT:
                mItemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                mVerticalPadding = 5;
                break;
            case Settings.TEXT_SIZE_LARGE:
                mItemText.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                mVerticalPadding = 10;
                break;
        }
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((AppCompatActivity) cxt).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mItemText.setMaxWidth(displayMetrics.widthPixels - ((int) (displayMetrics.scaledDensity * 80.0f)));

        setOnClickListener(this);
        setOnLongClickListener(this);

        setOrientation(LinearLayout.VERTICAL);

        LinearLayout layout = new LinearLayout(cxt);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setVerticalGravity(16);
        addView(layout);

        if (!mMovingViewPreview) {
            View view = new View(cxt);
            view.setLayoutParams(new ViewGroup.LayoutParams(-1, 1));
            view.setBackgroundColor(Color.BLACK);
            addView(view);
        }

        createButtons(cxt);

        mItemOptionsRowLeft = new LinearLayout(cxt);
        mItemOptionsRowLeft.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(mItemOptionsRowLeft);

        LinearLayout.LayoutParams textLayoutParams = new LinearLayout.LayoutParams(-2, -2);
        textLayoutParams.setMargins(0, mVerticalPadding, 0, mVerticalPadding);
        layout.addView(mItemText, textLayoutParams);

        mItemOptionsRow = new LinearLayout(cxt);
        mItemOptionsRow.setOrientation(LinearLayout.HORIZONTAL);
        RelativeLayout.LayoutParams iorp = new RelativeLayout.LayoutParams(-2, -2);
        iorp.setMargins(0, 0, 5, 0);
        iorp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        RelativeLayout iorl = new RelativeLayout(cxt);
        iorl.addView(mItemOptionsRow, iorp);
        layout.addView(iorl);

        updateView();
    }

    private void createButtons(Context cxt) {
        mCheckBox = new CheckBox(cxt);
        mUpButton = new ImageButton(cxt);
        mDownButton = new ImageButton(cxt);
        mMoveButton = new ImageButton(cxt);
        mDeleteButton = new ImageButton(cxt);
        View view = new View(cxt);
        view.setLayoutParams(new ViewGroup.LayoutParams(30, -1));
        mUpButton.setBackgroundResource(R.drawable.ic_action_collapse);
        mDownButton.setBackgroundResource(R.drawable.ic_action_expand);
        mMoveButton.setBackgroundResource(R.drawable.action_move);
        mDeleteButton.setBackgroundResource(R.drawable.ic_action_discard);
        if (!mMovingViewPreview) {
            mCheckBox.setOnClickListener(this);
            mUpButton.setOnClickListener(this);
            mUpButton.setOnLongClickListener(this);
            mDownButton.setOnClickListener(this);
            mDownButton.setOnLongClickListener(this);
            mDeleteButton.setOnClickListener(this);
            mMoveButton.setOnTouchListener(this);
        }
    }

    // Set item transparency
    private void setAlpha() {
        float f = 1; // Completely opague

        if (mItem.mDone && Settings.getBool(Settings.greyCheckedItems))
            // Greyed out
            f = 0.5f;
        else if (mItem == mItem.getChecklist().getMovingItem() && !mMovingViewPreview)
            // Moving item
            f = 0.2f;

        mItemText.setAlpha(f);
        mItemOptionsRow.setAlpha(f);
        mItemOptionsRowLeft.setAlpha(f);
    }

    private void setStrikeThrough() {
        if (!mItem.mDone || !Settings.getBool(Settings.strikeThroughCheckedItems)) {
            TextView textView = mItemText;
            textView.setPaintFlags(textView.getPaintFlags() & -17);
            return;
        }
        TextView textView2 = mItemText;
        textView2.setPaintFlags(textView2.getPaintFlags() | 16);
    }

    void setItem(Checklist.ChecklistItem item) {
        mItem = item;
    }

    Checklist.ChecklistItem getItem() {
        return mItem;
    }

    public void updateView() {
        mItemText.setText(mItem.getText());
        mItemOptionsRow.removeAllViews();
        mItemOptionsRowLeft.removeAllViews();
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        int i = mVerticalPadding;
        layoutParams.setMargins(0, i, 0, i);
        if (Settings.getBool(Settings.checkBoxOnLeftSide)) {
            mItemOptionsRowLeft.addView(mMoveButton, layoutParams);
            mItemOptionsRowLeft.addView(mCheckBox, layoutParams);
        } else {
            mItemOptionsRow.addView(mCheckBox, layoutParams);
            mItemOptionsRow.addView(mMoveButton, layoutParams);
        }
        mCheckBox.setChecked(mItem.mDone);
        setAlpha();
        setStrikeThrough();
        if (mMovingViewPreview)
            setBackgroundColor(Color.WHITE);
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        Log.d(TAG, "OnTouch " + motionEvent.getAction() + " " + Integer.toHexString(System.identityHashCode(this)));
        if (view != mMoveButton) {
            return false;
        }
        if (motionEvent.getAction() != 0) {
            return true;
        }
        Log.d(TAG, "MoveButton Down " + mItemText.getText());
        mItem.getChecklist().setMovingItem(mItem);
        return true;
    }

    /**
     * Handle all the clicks on the controls in an item row
     */
    public void onClick(View view) {
        Log.d(TAG, "OnClick");
        if (view == mUpButton) {
            moveUp();
        }
        if (view == mDownButton) {
            moveDown();
        }
        if (view == mDeleteButton) {
            discardItem();
        }
        CheckBox checkBox = mCheckBox;
        if (view == checkBox)
            setChecked(checkBox.isChecked());
        else if (view == this && Settings.getBool(Settings.entireRowTogglesItem))
            setChecked(!mCheckBox.isChecked());
    }

    public boolean onLongClick(View view) {
        if (view == mUpButton) {
            moveTop();
            return true;
        } else if (view == mDownButton) {
            moveBottom();
            return true;
        } else if (view != this) {
            return false;
        } else {
            showMenu();
            return true;
        }
    }

    private void showMenu() {
        PopupMenu popupMenu = new PopupMenu(mContext, this);
        popupMenu.inflate(R.menu.checklist_item_popup);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.action_delete) {
                    discardItem();
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

    public void setChecked(boolean z) {
        if (!Settings.getBool(Settings.autoDeleteCheckedItems) || !z) {
            mCheckBox.setChecked(z);
            mItem.mDone = z;
            // Update the list!
            mItem.getChecklist().notifyItemChecked();
            return;
        }
        discardItem();
    }

    private void discardItem() {
        mItem.getChecklist().remove(mItem);
    }

    private void moveUp() {
        mItem.getChecklist().moveItemToPosition(mItem, mItem.getChecklist().indexOf(mItem) - 1);
    }

    private void moveDown() {
        mItem.getChecklist().moveItemToPosition(mItem, mItem.getChecklist().indexOf(mItem) + 1);
    }

    private void moveTop() {
        mItem.getChecklist().moveItemToPosition(mItem, 0);
    }

    private void moveBottom() {
        mItem.getChecklist().moveItemToPosition(mItem, mItem.getChecklist().size() - 1);
    }

    private void showRenameItemDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.edit_list_item);
        final EditText editText = new EditText(mContext);
        builder.setView(editText);
        editText.setText(mItemText.getText());
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
