/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.cdot.lists.EntryListActivity;
import com.cdot.lists.Lister;
import com.cdot.lists.R;
import com.cdot.lists.model.EntryListItem;

/**
 * Base class of views on list entries. Provides the basic functionality of a sortable text view.
 */
@SuppressLint("ViewConstructor")
public abstract class EntryListItemView extends RelativeLayout implements View.OnClickListener {
    private final String TAG = EntryListItemView.class.getSimpleName();

    // The item being moved
    protected EntryListItem mItem;
    // Activity this view belongs to
    protected EntryListActivity mActivity;

    /**
     * Constructor
     * @param item     the item this is a view of
     * @param cxt      the context for the view
     */
    EntryListItemView(EntryListItem item, EntryListActivity cxt) {
        super(cxt);
        mActivity = cxt;
        setItem(item);
    }

    /**
     * Get the item that this is a view of
     *
     * @return the item
     */
    public EntryListItem getItem() {
        return mItem;
    }

    /**
     * Set the item this is a view of
     *
     * @param item the item we are a view of
     */
    public void setItem(EntryListItem item) {
        mItem = item;
    }

    public Lister getLister() {
        return mActivity.getLister();
    }

    protected void checkpoint() {
        getItem().notifyChangeListeners();
        mActivity.checkpoint();
    }

    /**
     *
     * @param butt moveButton
     * @param menuR R.menu of the popup menu
     */
    @SuppressLint("ClickableViewAccessibility")
    void addItemListeners(ImageButton butt, int menuR) {
        butt.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "OnDrag " + mItem.getText());
                mActivity.mMovingItem = mItem;
            }
            return true;
        });
        setOnLongClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(getContext(), this);
            popupMenu.inflate(menuR);
            popupMenu.setOnMenuItemClickListener(menuItem -> onPopupMenuAction(menuItem.getItemId()));
            popupMenu.show();
            return true;
        });
        setOnClickListener(this);
    }

    /**
     * Called to update the row view when settings have changed
     */
    public void updateView() {
        // Update the item text
        TextView it = findViewById(R.id.item_text);
        String t = mItem.getText();
        it.setText(t);
        setTextFormatting(it);
        ImageButton mb = findViewById(R.id.move_button);
        mb.setVisibility(mActivity.canManualSort() ? View.VISIBLE : View.GONE);
    }

    /**
     * Format the text according to current status of the item. Base class handles global settings.
     */
    protected void setTextFormatting(TextView it) {
        switch (getLister().getInt(Lister.PREF_TEXT_SIZE_INDEX)) {
            case Lister.TEXT_SIZE_SMALL:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                break;
            default:
            case Lister.TEXT_SIZE_MEDIUM:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                break;
            case Lister.TEXT_SIZE_LARGE:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                break;
        }
    }

    /**
     * Handle a popup menu action. Default is a NOP.
     *
     * @param action the action to handle
     * @return true if the action was handled
     */
    protected boolean onPopupMenuAction(int action) {
        return false;
    }
}
