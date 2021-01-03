/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view

import android.annotation.SuppressLint
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import com.cdot.lists.EntryListActivity
import com.cdot.lists.Lister
import com.cdot.lists.R
import com.cdot.lists.model.EntryListItem

/**
 * Base class of views on list entries. Provides the basic functionality of a sortable text view.
 */
@SuppressLint("ViewConstructor")
abstract class EntryListItemView
internal constructor(var it : EntryListItem, protected var activity: EntryListActivity) : RelativeLayout(activity), View.OnClickListener {

    companion object {
        private val TAG = EntryListItemView::class.simpleName
    }

    // Get the item that this is a view of
    internal var item : EntryListItem = it

    val lister: Lister
        get() = activity.lister

    protected fun checkpoint() {
        item.notifyChangeListeners()
        activity.checkpoint()
    }

    /**
     *
     * @param butt moveButton
     * @param menuR R.menu of the popup menu
     */
    @SuppressLint("ClickableViewAccessibility")
    fun addItemListeners(butt: ImageButton, menuR: Int) {
        butt.setOnTouchListener { view: View?, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "OnDrag " + item.text)
                activity.movingItem = item
            }
            true
        }
        setOnLongClickListener { view: View? ->
            val popupMenu = PopupMenu(context, this)
            popupMenu.inflate(menuR)
            popupMenu.setOnMenuItemClickListener { menuItem: MenuItem -> onPopupMenuAction(menuItem.itemId) }
            popupMenu.show()
            true
        }
        setOnClickListener(this)
    }

    /**
     * Called to update the row view when settings have changed
     */
    open fun updateView() {
        val it = findViewById<TextView>(R.id.item_text)!!
        it.text = item.text
        setTextFormatting(it)
        val mb = findViewById<ImageButton>(R.id.move_button)!!
        mb.visibility = if (activity.canManualSort()) View.VISIBLE else View.GONE
    }

    /**
     * Format the text according to current status of the item. Base class handles global settings.
     */
    protected open fun setTextFormatting(it: TextView) {
        when (lister.getInt(Lister.PREF_TEXT_SIZE_INDEX)) {
            Lister.TEXT_SIZE_SMALL -> it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
            Lister.TEXT_SIZE_MEDIUM -> it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
            Lister.TEXT_SIZE_LARGE -> it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
            else -> it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
        }
    }

    /**
     * Handle a popup menu action. Default is a NOP.
     *
     * @param action the action to handle
     * @return true if the action was handled
     */
    protected open fun onPopupMenuAction(action: Int): Boolean {
        return false
    }
}