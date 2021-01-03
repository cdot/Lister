/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cdot.lists.EntryListActivity
import com.cdot.lists.Lister
import com.cdot.lists.R
import com.cdot.lists.databinding.ChecklistItemViewBinding
import com.cdot.lists.model.Checklist
import com.cdot.lists.model.ChecklistItem
import com.cdot.lists.model.EntryListItem

/**
 * View for a single item in a checklist, and for moving same.
 */
@SuppressLint("ViewConstructor")
class ChecklistItemView(it: EntryListItem, // item we're moving
                        private val isMoving: Boolean,  // True if this view is of an item being moved
                        elActivity: EntryListActivity) : EntryListItemView(it, elActivity) {
    // True if the checkbox is on the right (which is where the basic layout has it)
    private var isCheckboxOnRight: Boolean
    private val binding: ChecklistItemViewBinding

    override fun onClick(view: View) {
        if (!isMoving && lister.getBool(Lister.PREF_ENTIRE_ROW_TOGGLES)) {
            val cb = binding.checklistCheckbox
            if (setChecked(!cb.isChecked)) {
                Log.d(TAG, "Item toggled")
                checkpoint()
            }
        }
    }

    override fun setTextFormatting(it: TextView) {
        super.setTextFormatting(it)

        // Transparency
        var f = TRANSPARENCY_OPAQUE // Completely opague
        if (item.getFlag(ChecklistItem.IS_DONE) && lister.getBool(Lister.PREF_GREY_CHECKED)) // Greyed out
            f = TRANSPARENCY_GREYED else if (!isMoving && item === activity.movingItem) {
            // Item being moved (but NOT the moving view)
            f = TRANSPARENCY_FAINT
            binding.rightLayout.alpha = f
            binding.leftLayout.alpha = f
        }
        it.alpha = f

        // Strike through
        if (!item.getFlag(ChecklistItem.IS_DONE) || !lister.getBool(Lister.PREF_STRIKE_CHECKED)) it.paintFlags = it.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv() else it.paintFlags = it.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    }

    override fun updateView() {
        super.updateView()
        val left: ViewGroup = binding.leftLayout
        val right: ViewGroup = binding.rightLayout
        val checkBox = binding.checklistCheckbox
        val moveButton = binding.moveButton
        if (lister.getBool(Lister.PREF_LEFT_HANDED) && isCheckboxOnRight) {
            // Move checkbox to left panel
            right.removeView(checkBox)
            left.removeView(moveButton)
            left.addView(checkBox)
            right.addView(moveButton)
            isCheckboxOnRight = false
        } else if (!lister.getBool(Lister.PREF_LEFT_HANDED) && !isCheckboxOnRight) {
            // Move checkbox to right panel
            left.removeView(checkBox)
            right.removeView(moveButton)
            right.addView(checkBox)
            left.addView(moveButton)
            isCheckboxOnRight = true
        }
        checkBox.isChecked = item.getFlag(ChecklistItem.IS_DONE)
    }

    override fun onPopupMenuAction(action: Int): Boolean {
        when(action) {
            R.id.action_delete -> {
                val list = item.parent!!
                list.newUndoSet()
                list.remove(item, true)
                Log.d(TAG, "item deleted")
                list.notifyChangeListeners()
                checkpoint()
            }
            R.id.action_rename -> {
                val builder = AlertDialog.Builder(context)
                builder.setTitle(R.string.edit_list_item)
                val editText = EditText(context)
                editText.isSingleLine = true
                editText.setText(item.text)
                builder.setView(editText)
                builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                    item.text = editText.text.toString()
                    Log.d(TAG, "item renamed")
                    item.notifyChangeListeners()
                    checkpoint()
                }
                builder.setNegativeButton(R.string.cancel, null)
                builder.show()
            }
            else -> return false
        }
        return true
    }

    /**
     * Handle checking / unchecking a single item. Notifies change listeners.
     *
     * @param isChecked the check status
     */
    private fun setChecked(isChecked: Boolean): Boolean {
        val list = item.parent as Checklist?
        if (list!!.getFlag(Checklist.DELETE_CHECKED) && isChecked) {
            val el = item.parent!!
            el.newUndoSet()
            el.remove(item, true)
            el.notifyChangeListeners()
            return true
        }
        binding.checklistCheckbox.isChecked = isChecked
        if (item.getFlag(ChecklistItem.IS_DONE) != isChecked) {
            if (isChecked) item.setFlag(ChecklistItem.IS_DONE) else item.clearFlag(ChecklistItem.IS_DONE)
            item.notifyChangeListeners()
            return true
        }
        return false
    }

    companion object {
        private val TAG = ChecklistItemView::class.simpleName
        private const val TRANSPARENCY_OPAQUE = 1f
        private const val TRANSPARENCY_GREYED = 0.5f
        private const val TRANSPARENCY_FAINT = 0.2f
    }

    init {
        item = it
        binding = ChecklistItemViewBinding.inflate(LayoutInflater.from(elActivity), this, true)
        if (!isMoving) {
            addItemListeners(binding.moveButton, R.menu.checklist_item_popup)
            binding.checklistCheckbox.setOnClickListener { view: View? ->
                if (setChecked(binding.checklistCheckbox.isChecked)) {
                    Log.d(TAG, "item checked")
                    if (binding.checklistCheckbox.isChecked) item.setFlag(ChecklistItem.IS_DONE) else item.clearFlag(ChecklistItem.IS_DONE)
                    checkpoint()
                }
            }
        }
        isCheckboxOnRight = true
        updateView()
    }
}