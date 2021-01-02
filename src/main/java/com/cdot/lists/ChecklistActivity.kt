/*
 * Copyright © 2020 C-Dot Consultants
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.cdot.lists

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.cdot.lists.databinding.ChecklistActivityBinding
import com.cdot.lists.model.*
import com.cdot.lists.preferences.PreferencesActivity
import com.cdot.lists.view.ChecklistItemView
import com.cdot.lists.view.EntryListItemView
import org.json.JSONException

/**
 * Activity for managing interactions with a checklist of items
 */
class ChecklistActivity : EntryListActivity() {
    // When in edit mode, sorting and moving checked items is disabled
    var mInEditMode = false

    private lateinit var mBinding: ChecklistActivityBinding // init in onCreate
    override lateinit var list: EntryList  // init in onCreate

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putInt(UID_EXTRA, list.sessionUID)
    }

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)
        val uid = state.getInt(UID_EXTRA)
        if (uid > 0) list = lister.lists.findBySessionUID(uid) as EntryList
    }

    // AppCompatActivity
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        mBinding = ChecklistActivityBinding.inflate(layoutInflater)
        val intent = intent
        val uid = intent.getIntExtra(UID_EXTRA, -1)
        val lists: EntryList = lister.lists
        list = lists.findBySessionUID(uid) as EntryList
        Log.d(TAG, "onCreate list $list")
        makeAdapter()
        mBinding.addItemET.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        mBinding.addItemET.setOnEditorActionListener { textView: TextView?, i: Int, keyEvent: KeyEvent? ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                val text = mBinding.addItemET.text.toString()
                if (text.trim { it <= ' ' }.isNotEmpty()) {
                    if (list.getFlag(EntryList.WARN_ABOUT_DUPLICATES)) {
                        val found = list.findByText(text, false)
                        if (found == null)
                            addItem(text)
                        else
                            promptSimilarItem(text, found.text)
                    } else
                        addItem(text)
                }
                return@setOnEditorActionListener true
            }
            false
        }
        mBinding.addItemET.imeOptions = EditorInfo.IME_ACTION_DONE
        enableEditMode(list.size() == 0)
        setContentView(mBinding.root)
        supportActionBar!!.title = list.text
    }

    // override ListerActivity
    override val helpAsset: Int = R.raw.checklist_help

    // override EntryListActivity
    override val listView: ListView
        get() = mBinding.itemListView

    // ListerActivity
    override val rootView: View
        get() = mBinding.root

    // implements EntryListActivity
    override fun makeItemView(movingItem: EntryListItem, drag: Boolean): EntryListItemView {
        return ChecklistItemView(movingItem, drag, this)
    }

    // Activity
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //Log.d(TAG, "onCreateOptionsMenu");
        menuInflater.inflate(R.menu.checklist, menu)
        return true
    }

    // EntryListActivity
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        val it = menu.findItem(R.id.action_edit)
        if (mInEditMode) {
            it.setIcon(R.drawable.ic_action_item_add_off)
            it.setTitle(R.string.action_item_add_off)
        } else {
            it.setIcon(R.drawable.ic_action_item_add_on)
            it.setTitle(R.string.action_item_add_on)
        }
        menu.findItem(R.id.action_preferences).isEnabled = !mInEditMode
        menu.findItem(R.id.action_check_all).isEnabled = (list as Checklist).countFlaggedEntries(ChecklistItem.IS_DONE) < list.size()
        menu.findItem(R.id.action_uncheck_all).isEnabled = (list as Checklist).countFlaggedEntries(ChecklistItem.IS_DONE) > 0
        menu.findItem(R.id.action_undo_delete).isEnabled = list.removeCount > 0
        menu.findItem(R.id.action_delete_checked).isEnabled = (list as Checklist).countFlaggedEntries(ChecklistItem.IS_DONE) > 0
        return true
    }

    // EntryListActivity
    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        // Beware dispatchTouchEvent stealing events
        //Log.d(TAG, "onOptionsItemSelected");
        if (super.onOptionsItemSelected(menuItem)) return true
        val checklist = list as Checklist
        when (menuItem.itemId) {
            R.id.action_check_all -> if (checklist.setFlagOnAll(ChecklistItem.IS_DONE, true)) {
                list.notifyChangeListeners()
                Log.d(TAG, "check all")
                checkpoint()
            }

            R.id.action_delete_checked -> {
                val deleted = checklist.deleteAllFlagged(ChecklistItem.IS_DONE)
                if (deleted > 0) {
                    list.notifyChangeListeners()
                    Log.d(TAG, "checked deleted")
                    checkpoint()
                    reportShort(R.string.snack_deleted, deleted)
                    if (list.size() == 0) {
                        enableEditMode(true)
                        list.notifyChangeListeners()
                        checkpoint()
                    }
                }
            }

            R.id.action_edit -> enableEditMode(!mInEditMode)

            R.id.action_rename_list -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.rename_list)
                builder.setMessage(R.string.enter_new_name_of_list)
                val editText = EditText(this)
                editText.isSingleLine = true
                editText.setText(list.text)
                builder.setView(editText)
                builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                    Log.d(TAG, "list renamed")
                    list.text = editText.text.toString()
                    lister.lists.notifyChangeListeners()
                    checkpoint()
                }
                builder.setNegativeButton(R.string.cancel, null)
                builder.show()
            }

            R.id.action_uncheck_all ->
                if (checklist.setFlagOnAll(ChecklistItem.IS_DONE, false)) {
                    Log.d(TAG, "uncheck all")
                    list.notifyChangeListeners()
                    checkpoint()
                }

            R.id.action_undo_delete -> {
                val undone = checklist.undoRemove()
                if (undone == 0) reportShort(R.string.snack_no_deleted_items) else {
                    Log.d(TAG, "delete undone")
                    list.notifyChangeListeners()
                    checkpoint()
                    reportShort(R.string.snack_restored, undone)
                }
            }

            R.id.action_share_list ->
                try {
                    // This doesn't have to be fast, so we use the clunky but safe mechanism of serialising to
                    // JSON to simplify parenthood
                    val container = Checklists()
                    container.addChild(Checklist().fromJSON(list.toJSON()))
                    share(container, list.text)
                } catch (je: JSONException) {
                    Log.e(TAG, Lister.stringifyException(je))
                }

            R.id.action_preferences -> {
                val intent = Intent(this, PreferencesActivity::class.java)
                intent.putExtra(UID_EXTRA, list.sessionUID)
                startActivityForResult(intent, REQUEST_PREFERENCES)
            }

            else -> return super.onOptionsItemSelected(menuItem)
        }
        return true
    }

    // EntryListActivity
    override fun canManualSort(): Boolean {
        return !list.getFlag(Checklist.CHECKED_AT_END) && super.canManualSort()
    }

    override fun updateDisplay() {
        // Could simplify to runOnUiThread { notifyAdapter() }
        super.updateDisplay()
        supportActionBar?.title = list.text
    }

    // EntryListActivity
    override val displayOrder: MutableList<EntryListItem>
        get() {
            if (mInEditMode) return ArrayList(list.data) // unsorted list
            val dl = super.displayOrder // get sorted list
            if (list.getFlag(Checklist.CHECKED_AT_END)) {
                var top = dl.size
                var i = 0
                while (i < top) {
                    val item = dl[i] as ChecklistItem
                    if (item.getFlag(ChecklistItem.IS_DONE)) {
                        dl.add(dl.removeAt(i))
                        top--
                    } else i++
                }
            }
            return dl
        }

    /**
     * Turn editing "mode" on/off
     *
     * @param isOn new state
     */
    private fun enableEditMode(isOn: Boolean) {
        mInEditMode = isOn
        notifyAdapter()
        invalidateOptionsMenu()

        // Show/hide soft keyboard
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (mInEditMode) {
            mBinding.addItemET.visibility = View.VISIBLE
            mBinding.addItemET.isFocusable = true
            mBinding.addItemET.isFocusableInTouchMode = true
            mBinding.addItemET.requestFocus()
            inputMethodManager.showSoftInput(mBinding.addItemET, InputMethodManager.SHOW_IMPLICIT)
        } else {
            mBinding.addItemET.visibility = View.INVISIBLE
            var currentFocus = currentFocus
            if (currentFocus == null) currentFocus = mBinding.addItemET
            inputMethodManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }

    /**
     * Handle adding an item after it's confirmed
     *
     * @param str the text of the item
     */
    override fun addItem(str: String) {
        val item = ChecklistItem(str)
        list.addChild(item)
        Log.d(TAG, "item $item added to $list")
        mBinding.addItemET.setText("")
        mBinding.itemListView.smoothScrollToPosition(displayOrder.indexOf(item))
        checkpoint()
    }

    companion object {
        private val TAG = ChecklistActivity::class.simpleName
    }
}