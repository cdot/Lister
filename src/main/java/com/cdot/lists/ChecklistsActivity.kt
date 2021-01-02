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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import com.cdot.lists.Lister.Companion.PREF_FILE_URI
import com.cdot.lists.databinding.ChecklistsActivityBinding
import com.cdot.lists.model.Checklist
import com.cdot.lists.model.EntryList
import com.cdot.lists.model.EntryListItem
import com.cdot.lists.preferences.PreferencesActivity
import com.cdot.lists.view.ChecklistsItemView
import com.cdot.lists.view.EntryListItemView

/**
 * Activity that displays a list of checklists. The checklists are stored in the MainActivity.
 */
class ChecklistsActivity : EntryListActivity() {
    private lateinit var mBinding: ChecklistsActivityBinding

    // EntryListActivity
    override val list: EntryList
        get() = lister.lists

    // ListerActivity
    override val rootView: View
        get() = mBinding.root

    // AppCompatActivity
    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        mBinding = ChecklistsActivityBinding.inflate(layoutInflater) // lateinit
        makeAdapter()
        setContentView(mBinding.root)
        if (BuildConfig.DEBUG)
            lister.getUri(PREF_FILE_URI)?.let { reportIndefinite(R.string.report_uri, it) }
    }

    // EntryListActivity
    override fun makeItemView(movingItem: EntryListItem, drag: Boolean): EntryListItemView {
        return ChecklistsItemView(movingItem, drag, this)
    }

    // override ListerActivity
    override val helpAsset: Int = R.raw.checklists_help

    // EntryListActivity
    override fun onListChanged(item: EntryListItem) {
        super.onListChanged(item)
        runOnUiThread {
            val sz = list.size()
            mBinding.listsMessage.visibility = if (sz == 0) View.VISIBLE else View.GONE
            mBinding.itemListView.visibility = if (sz == 0) View.GONE else View.VISIBLE
        }
    }

    // Action menu handling
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //Log.d(TAG, "onCreateOptionsMenu");
        menuInflater.inflate(R.menu.checklists, menu)
        return true
    }

    // Action menu handling
    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        //Log.d(TAG, "onOptionsItemSelected");
        if (super.onOptionsItemSelected(menuItem)) return true
        when(menuItem.itemId) {
            R.id.action_import_lists -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                // import from any file type - we can either parse it, or we can't
                intent.type = "*/*" // see https://developer.android.com/guide/components/intents-common
                //intent.putExtra(EXTRA_MIME_TYPES, resources.getStringArray(R.array.share_format_mimetype))
                startActivityForResult(intent, REQUEST_IMPORT)
            }

            R.id.action_new_list -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.create_new_list)
                builder.setMessage(R.string.enter_name_of_new_list)
                val editText = EditText(this)
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                editText.isSingleLine = true
                builder.setView(editText)
                builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                    var text = editText.text.toString()
                    if (!text.trim { it <= ' ' }.isEmpty()) {
                        if (!lister.getBool(Lister.PREF_WARN_DUPLICATE)) {
                            val find = list.findByText(text, false)
                            if (find == null)
                                addItem(text)
                            else
                                promptSimilarItem(text, find.text)
                        } else
                            addItem(text)
                    }
                }
                builder.setNegativeButton(R.string.cancel, null)
                builder.show()
                editText.post {
                    editText.isFocusable = true
                    editText.isFocusableInTouchMode = true
                    editText.requestFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                }
            }

            R.id.action_share_lists ->
                share(list, resources.getString(R.string.default_share_filename))

            R.id.action_preferences ->
                startActivityForResult(Intent(this, PreferencesActivity::class.java), REQUEST_PREFERENCES)

            else -> return super.onOptionsItemSelected(menuItem)
        }
        return true
    }

    // EntryListActivity
    override val listView: ListView
        get() = mBinding.itemListView

    /**
     * Handle adding an item after it's confirmed
     *
     * @param str the text of the item
     */
    override fun addItem(str: String) {
        val item = Checklist(str)
        list.addChild(item)
        Log.d(TAG, "List $item added to $list")
        mBinding.itemListView.smoothScrollToPosition(displayOrder.indexOf(item))
        checkpoint()
    }

    companion object {
        private val TAG = ChecklistsActivity::class.simpleName
    }
}