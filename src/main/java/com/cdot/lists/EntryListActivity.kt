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

import android.content.DialogInterface
import android.content.Intent
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.cdot.lists.model.EntryList
import com.cdot.lists.model.EntryListItem
import com.cdot.lists.model.EntryListItem.Companion.NO_NAME
import com.cdot.lists.view.EntryListItemView
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.util.*

/**
 * Base class of list view activities.
 * The common code here supports moving items in the list and some base menu functionality.
 */
abstract class EntryListActivity : ListerActivity(), EntryListItem.ChangeListener {

    @JvmField
    @Transient
    var mMovingItem: EntryListItem? = null
    protected var mArrayAdapter: EntryListAdapter? = null

    @Transient
    private var mMovingView: EntryListItemView? = null

    // The list we're viewing
    abstract val list: EntryList
    abstract val listView: ListView

    // Set the common bindings, obtained from the ViewBinding, and create the array adapter
    // MUST be called from subclass onCreate immediately the view binding has been established
    protected fun makeAdapter() {
        mArrayAdapter = EntryListAdapter(this)
        listView.adapter = mArrayAdapter
    }

    override fun updateDisplay() {
        // Could simplify to runOnUiThread { notifyAdapter() }
        list.notifyChangeListeners()
        var t = list.text
        if (t == NO_NAME) t = getString(R.string.app_name)
        supportActionBar!!.title = t
    }

    /**
     * List contents have changed; notify the adapter
     */
    protected fun notifyAdapter() {
        mArrayAdapter!!.notifyDataSetChanged()
    }

    override fun onPause() {
        list.removeChangeListener(this)
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
        list.addChangeListener(this)
        list.notifyChangeListeners()
    }

    // implement EntryListItem.ChangeListener
    override fun onListChanged(item: EntryListItem) {
        runOnUiThread { notifyAdapter() }
    }

    /**
     * Get the list items in display order. Override to change the order of items in the list.
     *
     * @return the list. May be modified, but entries point to data source
     */
    protected open val displayOrder: MutableList<EntryListItem>
        get() {
            val dl: MutableList<EntryListItem> = ArrayList<EntryListItem>()
            dl.addAll(list.data)
            if (list.getFlag(EntryList.DISPLAY_SORTED)) {
                dl.sortWith(object : Comparator<EntryListItem> {
                    override fun compare(o1: EntryListItem, o2: EntryListItem): Int {
                        return o1.text.compareTo(o2.text, ignoreCase = true)
                    }
                })
            }
            return dl
        }

    abstract fun addItem(str: String)

    /**
     * Prompt for similar item already in the list
     *
     * @param proposed the text of the proposed item
     * @param similar  the text of a similar item already in the list
     */
    protected fun promptSimilarItem(proposed: String, similar: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.similar_item_already_in_list)
        builder.setMessage(getString(R.string.similar_item_x_already_in_list, similar, proposed))
        builder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int -> addItem(proposed) }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    /**
     * Used in ItemView to determine if list manual sort controls should be shown
     *
     * @return true if the list can be manually sorted
     */
    open fun canManualSort(): Boolean {
        return !list.getFlag(EntryList.DISPLAY_SORTED) && !list.getFlag("moveCheckedToEnd")
    }

    /**
     * Make a view that can be used to display an item in the list.
     *
     * @param movingItem the item being moved
     * @param drag true to make it a moving view. This will be the same type
     * as a normal entry in the list but will have no event handlers and may have display differences.
     * @return a View that is used to display the dragged item
     */
    protected abstract fun makeItemView(movingItem: EntryListItem, drag: Boolean): EntryListItemView

    /**
     * Moving items in the list
     *
     * @param motionEvent the event
     * @return true if the event is handled
     */
    @Synchronized
    override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
        // Check the item can be moved
        if (mMovingItem == null) return super.dispatchTouchEvent(motionEvent)
        val movingItem = mMovingItem!!
        if (!movingItem.isMoveable) return super.dispatchTouchEvent(motionEvent)

        // get screen position of the ListView and the Activity
        val iArr = IntArray(2)
        listView.getLocationOnScreen(iArr)
        val listViewTop = iArr[1]
        val vp = listView.parent
        val listLayout = vp as ViewGroup
        listLayout.getLocationOnScreen(iArr)
        val activityTop = iArr[1]

        // Convert touch location to relative to the ListView
        val y = motionEvent.y.toInt() - listViewTop
        val list = list

        // Get the index of the item being moved in the items list
        val itemIndex = list.indexOf(movingItem)

        // Get the index of the moving view in the list of views. It will stay there until
        // the drag is released.
        var viewIndex = listView.childCount - 1
        while (viewIndex >= 0) {
            if ((listView.getChildAt(viewIndex) as EntryListItemView).item === movingItem) break
            viewIndex--
        }
        if (viewIndex < 0) throw RuntimeException("Can't find view for item: $movingItem")
        //Log.d(TAG, "Moving item at " + itemIndex + " viewIndex " + viewIndex);
        var prevBottom = Int.MIN_VALUE
        if (viewIndex > 0) // Not first view
            prevBottom = listView.getChildAt(viewIndex - 1).bottom
        var nextTop = Int.MAX_VALUE
        if (viewIndex < listView.childCount - 1) // Not last view
            nextTop = listView.getChildAt(viewIndex + 1).top
        val halfItemHeight = listView.getChildAt(viewIndex).height / 2
        var moveTo = itemIndex
        if (y < prevBottom) moveTo-- else if (y > nextTop) moveTo++

        //Log.d(TAG, "Compare " + y + " with " + prevBottom + " and " + nextTop + " moveTo " + moveTo);
        if (moveTo != itemIndex && moveTo >= 0 && moveTo < list.size()) {
            Log.d(TAG, "Moved " + movingItem.text + " from " + itemIndex + " to " + moveTo)
            list.remove(movingItem, false)
            list.put(moveTo, movingItem)
            list.notifyChangeListeners()
            checkpoint()
        }
        if (motionEvent.action == MotionEvent.ACTION_MOVE) {
            if (mMovingView == null) {
                // Drag is starting
                //Log.d(TAG, "dispatchTouchEvent adding moving view ");
                mMovingView = makeItemView(movingItem, true)
                // addView is not supported in AdapterView, so can't add the movingView there.
                // Instead have to add to the activity and adjust margins accordingly
                listLayout.addView(mMovingView)
            }
            if (y < halfItemHeight) listView.smoothScrollToPosition(itemIndex - 1)
            if (y > listView.height - halfItemHeight) listView.smoothScrollToPosition(itemIndex + 1)
            // Layout params for the parent, not for this view
            val lp: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            // Set the top margin to move the view to the right place relative to the Activity
            lp.setMargins(0, listViewTop - activityTop + y - halfItemHeight, 0, 0)
            mMovingView!!.layoutParams = lp
        }
        if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
            listLayout.removeView(mMovingView)
            mMovingItem = null
            notifyAdapter()
            mMovingView = null
        }
        return true
    }

    // Activity
    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        val it = menuItem.itemId
        when (it) {
            R.id.action_alpha_sort -> {
                Log.d(TAG, "alpha sort option selected")
                if (list.getFlag(EntryList.DISPLAY_SORTED)) list.clearFlag(EntryList.DISPLAY_SORTED) else list.setFlag(EntryList.DISPLAY_SORTED)
                mMessageHandler.sendMessage(mMessageHandler.obtainMessage(MESSAGE_UPDATE_DISPLAY))
                checkpoint()
            }
            R.id.action_help -> {
                val hint = Intent(this, HelpActivity::class.java)
                hint.putExtra(HelpActivity.ASSET_EXTRA, helpAsset)
                startActivity(hint)
            }
            else -> return false
        }
        return true
    }

    // AppCompatActivity
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        var menuItem = menu.findItem(R.id.action_alpha_sort)
        if (list.getFlag(EntryList.DISPLAY_SORTED)) {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_off)
            menuItem.setTitle(R.string.action_alpha_sort_off)
        } else {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_on)
            menuItem.setTitle(R.string.action_alpha_sort_on)
        }
        menuItem = menu.findItem(R.id.action_save)
        menuItem.isVisible = lister.getBool(Lister.PREF_LAST_STORE_FAILED)
        return true
    }

    /**
     * Adapter for the list. This is only created when the list is actually displayed.
     */
    inner class EntryListAdapter internal constructor(act: AppCompatActivity?) : ArrayAdapter<EntryListItem?>(act!!, 0) {
        // ArrayAdapter
        override fun getView(i: Int, convertView: View?, viewGroup: ViewGroup): View {
            val dl = displayOrder // get sorted list
            if (i < dl.size) {
                val itemView = if (convertView == null) makeItemView(dl[i], false) else convertView as EntryListItemView
                itemView.item = dl[i]
                itemView.updateView()
                return itemView
            } else if (convertView != null)
                return convertView;
            else {
                // Sometimes on startup we get here, with list size 0 and item index 1. Why?
                throw Error("Empty display order")
            }
        }

        override fun getCount(): Int {
            return list.size()
        }
    }

    /**
     * Share the list e.g. to email in JSON format
     * @param the list to share. We can't use the list for this view, as it might have some
     * preprocessing before it's ready to share
     */
    internal fun share(list: EntryList, listName: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.export_format)
        val picker = Spinner(this)
        // Helper for indexing the array of share formats, must be final for inner class access
        var mPlace = 0
        val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, resources.getStringArray(R.array.share_format_description))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        picker.adapter = adapter
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View, position: Int, id: Long) {
                mPlace = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        builder.setView(picker)
        builder.setPositiveButton(R.string.ok) { dialog: DialogInterface?, id: Int ->
            doShare(list, listName, resources.getStringArray(R.array.share_format_mimetype)[mPlace])
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun doShare(list: EntryList, listName: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TITLE, listName) // Dialog title
        intent.type = mimeType
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: resources.getString(R.string.default_share_mimetype)
        val fileName = listName.replace("[/\u0000]".toRegex(), "_") + "." + ext
        // WTF! The EXTRA_SUBJECT is used as the document title for Drive saves!
        intent.putExtra(Intent.EXTRA_SUBJECT, fileName)

        // text body e.g. for email
        val text = list.toPlainString("")
        intent.putExtra(Intent.EXTRA_TEXT, text)
        try {
            // Write a local file for the attachment
            val sendFile = File(getExternalFilesDir(null), fileName)
            val w: Writer = FileWriter(sendFile)
            when (mimeType) {
                "text/plain" ->                         // Re-write the text body in the attachment
                    w.write(text)
                "text/csv" -> {
                    val csvw = CSVWriter(w)
                    list.toCSV(csvw)
                }
                "application/json" -> {
                    w.write(list.toJSON().toString())
                }
                else -> throw Exception("Unrecognised share format")
            }
            w.write(list.toJSON().toString())

            w.close()

            // Expose the local file using a URI from the FileProvider, and add the URI to the intent
            // See https://medium.com/@ali.muzaffar/what-is-android-os-fileuriexposedexception-and-what-you-can-do-about-it-70b9eb17c6d0
            val uri = FileProvider.getUriForFile(this, "com.cdot.lists.provider", sendFile)
            intent.putExtra(Intent.EXTRA_STREAM, uri)

            // using the chooser causes an exception to be reported in the debugger. However it
            // doesn't seem to affect the functionality.
            // See https://stackoverflow.com/questions/63723656/share-content-permission-denial-when-using-intent-createchooser
            startActivity(Intent.createChooser(intent, fileName))

        } catch (e: Exception) {
            Log.e(TAG, "Share failed " + Lister.stringifyException(e))
            reportIndefinite(R.string.failed_share)
        }
    }

    companion object {
        private val TAG = EntryListActivity::class.simpleName
    }
}