/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.cdot.lists.ChecklistActivity
import com.cdot.lists.EntryListActivity
import com.cdot.lists.ListerActivity
import com.cdot.lists.R
import com.cdot.lists.databinding.ChecklistsItemViewBinding
import com.cdot.lists.model.Checklist
import com.cdot.lists.model.Checklists
import com.cdot.lists.model.EntryListItem

/**
 * View of the title of a list
 */
@SuppressLint("ViewConstructor")
class ChecklistsItemView @SuppressLint("ClickableViewAccessibility") constructor(item: EntryListItem, isMoving: Boolean, cxt: EntryListActivity) : EntryListItemView(item, cxt) {
    var mBinding: ChecklistsItemViewBinding

    // View
    override fun onClick(view: View) {
        // A click on a list name will open that list in a ChecklistActivity
        val intent = Intent(context, ChecklistActivity::class.java)
        intent.putExtra(ListerActivity.UID_EXTRA, item.sessionUID)
        context.startActivity(intent)
    }

    // EntryListItemView
    override fun onPopupMenuAction(act: Int): Boolean {
        val checklists = item.parent as Checklists?
        val builder: AlertDialog.Builder
        if (act == R.id.action_delete) {
            builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.confirm_delete)
            builder.setMessage(context.getString(R.string.confirm_delete_list, item.text))
            builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, which_button: Int ->
                val el = item.parent
                el!!.remove(item, true)
                Log.d(TAG, "list deleted")
                el.notifyChangeListeners()
                checkpoint()
            }
            builder.setNegativeButton(R.string.cancel, null)
            builder.show()
        } else if (act == R.id.action_rename) {
            builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.rename_list)
            builder.setMessage(R.string.enter_new_name_of_list)
            val editText = EditText(context)
            editText.isSingleLine = true
            editText.setText(item.text)
            builder.setView(editText)
            builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                item.text = editText.text.toString()
                Log.d(TAG, "list renamed")
                item.parent!!.notifyChangeListeners()
                checkpoint()
            }
            builder.setNegativeButton(R.string.cancel, null)
            builder.show()
        } else if (act == R.id.action_copy) {
            val checklist = Checklist((item as Checklist))
            val newname = checklist.text + " (copy)"
            checklist.text = newname
            checklists!!.addChild(checklist)
            checklists.notifyChangeListeners()
            Log.d(TAG, "list copied")
            checkpoint()
        } else return false
        return true
    }

    companion object {
        private val TAG = ChecklistsItemView::class.java.simpleName
    }

    init {
        mBinding = ChecklistsItemViewBinding.inflate(LayoutInflater.from(cxt), this, true)
        if (!isMoving) addItemListeners(mBinding.moveButton, R.menu.checklists_popup)
        updateView()
    }
}