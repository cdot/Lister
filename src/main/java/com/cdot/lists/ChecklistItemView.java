package com.cdot.lists;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
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
    //private ImageButton mDeleteButton;
    //private ImageButton mUpButton;
    //private ImageButton mDownButton;
    private Checklist.ChecklistItem mItem;
    private LinearLayout mRight;
    private LinearLayout mLeft;
    private TextView mItemText;
    private ImageButton mMoveButton;
    private boolean mMovingViewPreview = false;
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

        setOnClickListener(this);
        setOnLongClickListener(this);

        // We are subclassing LinearLayout
        setOrientation(LinearLayout.VERTICAL);

        // Why do we need this additional layout? Why not make the container (which is a LinearLayout)
        // horizontal? It doesn't work without the inner layout, but unclear why.
        LinearLayout rowLayout = new LinearLayout(cxt);
        addView(rowLayout);

        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setVerticalGravity(Gravity.CENTER_VERTICAL);

        if (!mMovingViewPreview) {
            // TODO: Assume this is just to carry a background colour?
            View view = new View(cxt);
            view.setLayoutParams(new ViewGroup.LayoutParams(-1, 1));
            view.setBackgroundColor(Color.BLACK); // TODO: theme?
            addView(view);
        }

        mCheckBox = new CheckBox(cxt); // Added to mLeft or mRight in updateView
        mMoveButton = new ImageButton(cxt); // Added to mLeft or mRight in updateView

        //mUpButton = new ImageButton(cxt); // never used AFAICT
        //mDownButton = new ImageButton(cxt); // never used AFAICT
        //mDeleteButton = new ImageButton(cxt); // never used AFAICT

        // setBackgroundResource to keep the image button size default
        //mUpButton.setBackgroundResource(R.drawable.ic_action_collapse);
        //mDownButton.setBackgroundResource(R.drawable.ic_action_expand);
        mMoveButton.setBackgroundResource(R.drawable.action_move);
        //mDeleteButton.setBackgroundResource(R.drawable.ic_action_discard);
        if (!mMovingViewPreview) {
            mCheckBox.setOnClickListener(this);
            //mUpButton.setOnClickListener(this);
            //mUpButton.setOnLongClickListener(this);
            //mDownButton.setOnClickListener(this);
            //mDownButton.setOnLongClickListener(this);
            //mDeleteButton.setOnClickListener(this);
            mMoveButton.setOnTouchListener(this);
        }

        // A view for whatever is on the left side of the text
        mLeft = new LinearLayout(cxt);
        mLeft.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.addView(mLeft);

        // Now add the text
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

        // TODO: Shouldn't MATCH_PARENT achieve this?
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((AppCompatActivity) cxt).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mItemText.setMaxWidth(displayMetrics.widthPixels - ((int) (displayMetrics.scaledDensity * 80.0f)));

        LinearLayout.LayoutParams textLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textLayoutParams.setMargins(0, mVerticalPadding, 0, mVerticalPadding);
        rowLayout.addView(mItemText, textLayoutParams);

        // Finally whatever is on the right
        mRight = new LinearLayout(cxt);
        mRight.setOrientation(LinearLayout.HORIZONTAL);
        RelativeLayout.LayoutParams iorp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        iorp.setMargins(0, 0, 5, 0);
        iorp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        RelativeLayout iorl = new RelativeLayout(cxt);
        iorl.addView(mRight, iorp);
        rowLayout.addView(iorl);

        updateView();
    }

    private void setTextFormatting() {
        // Transparency
        float f = 1; // Completely opague

        if (mItem.mDone && Settings.getBool(Settings.greyCheckedItems))
            // Greyed out
            f = 0.5f;
        else if (mItem == mItem.getChecklist().getMovingItem() && !mMovingViewPreview)
            // Moving item
            f = 0.2f;

        mItemText.setAlpha(f);
        mRight.setAlpha(f);
        mLeft.setAlpha(f);

        // Strike through
        if (!mItem.mDone || !Settings.getBool(Settings.strikeThroughCheckedItems))
            mItemText.setPaintFlags(mItemText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        else
            mItemText.setPaintFlags(mItemText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    }

    void setItem(Checklist.ChecklistItem item) {
        mItem = item;
    }

    Checklist.ChecklistItem getItem() {
        return mItem;
    }

    public void updateView() {
        // Update the item text
        mItemText.setText(mItem.getText());
        setTextFormatting();

        // Rebuild the view based on left and right
        mRight.removeAllViews();
        mLeft.removeAllViews();
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, mVerticalPadding, 0, mVerticalPadding);
        if (Settings.getBool(Settings.checkBoxOnLeftSide)) {
            mLeft.addView(mMoveButton, layoutParams);
            mLeft.addView(mCheckBox, layoutParams);
        } else {
            mRight.addView(mCheckBox, layoutParams);
            mRight.addView(mMoveButton, layoutParams);
        }

        mCheckBox.setChecked(mItem.mDone);

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
        //if (view == mUpButton)
        //    moveUp();
        //if (view == mDownButton)
        //    moveDown();
        //if (view == mDeleteButton)
        //    mItem.getChecklist().remove(mItem);
        if (view == mCheckBox)
            setChecked(mCheckBox.isChecked());
        else if (view == this && Settings.getBool(Settings.entireRowTogglesItem))
            setChecked(!mCheckBox.isChecked());
    }

    public boolean onLongClick(View view) {
        //if (view == mUpButton) {
        //    moveTop();
        //    return true;
        //}
        // if (view == mDownButton) {
        //    moveBottom();
        //    return true;
        //}
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
            mCheckBox.setChecked(isChecked);
            mItem.mDone = isChecked;
        }
        // Update the list!
        // TODO: is this required?
        mItem.getChecklist().notifyItemChanged();
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
