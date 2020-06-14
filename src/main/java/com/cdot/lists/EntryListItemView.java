/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * Base class of views on list entries. Provides the basic functionality of a sortable text view.
 */
class EntryListItemView extends RelativeLayout implements View.OnClickListener {

    private final String TAG = "EntryListItemView";

    // True if this view is of an item being moved
    protected boolean mIsMoving;
    protected EntryListItem mItem;
    protected Context mContext;
    protected int mMenuResource;

    /**
     * Constructor
     * @param item the item this is a view of
     * @param isMoving whether this is the special case of an item that is being moved
     * @param cxt the context for the view
     */
    EntryListItemView(EntryListItem item, boolean isMoving, Context cxt, int rootResource, int menuResource) {
        super(cxt);
        inflate(getContext(), rootResource, this);
        mContext = cxt;
        mIsMoving = isMoving;
        mMenuResource = R.menu.checklist_item_popup;
        setItem(item);
    }

    /**
     * Get the item that this is a view of
     * @return the item
     */
    EntryListItem getItem() {
        return mItem;
    }

    /**
     * Set the item this is a view of
     * @param item the item we are a view of
     */
    void setItem(EntryListItem item) {
        mItem = item;
    }

    @SuppressLint("ClickableViewAccessibility")
    void addListeners() {
        ImageButton butt = findViewById(R.id.move_button);
        butt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d(TAG, "OnTouch " + motionEvent.getAction() + " " + Integer.toHexString(System.identityHashCode(this)));
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                    mItem.getContainer().setMovingItem(mItem);
                return true;
            }
        });
        setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showMenu();
                return true;
            }
        });
        setOnClickListener(this);
    }

    @Override // View.OnLongClickListener
    public void onClick(View view) {
        // implement in subclasses
    }

    public void updateView() {
        // Update the item text
        TextView it = findViewById(R.id.item_text);
        it.setText(mItem.getText());
        setTextFormatting();
    }

    protected void setTextFormatting() {
        TextView it = findViewById(R.id.item_text);
        int padding;
        // Size
        switch (Settings.getInt("textSizeIndex")) {
            case Settings.TEXT_SIZE_SMALL:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                padding = 0;
                break;
            default:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                padding = 5;
                break;
            case Settings.TEXT_SIZE_LARGE:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                padding = 10;
                break;
        }
        it.setPadding(0, padding, 0, padding);
    }

    protected boolean onAction(int action) { return false; }

    private void showMenu() {
        PopupMenu popupMenu = new PopupMenu(mContext, this);
        popupMenu.inflate(mMenuResource);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem menuItem) {
                return onAction(menuItem.getItemId());
            }
        });
        popupMenu.show();
    }
}
