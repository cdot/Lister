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
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cdot.lists.model.EntryList
import com.cdot.lists.model.EntryListItem
import com.cdot.lists.view.EntryListItemView
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
    open abstract val list: EntryList
    abstract val listView: ListView

    // Set the common bindings, obtained from the ViewBinding, and create the array adapter
    // MUST be called from subclass onCreate immediately the view binding has been established
    protected fun makeAdapter() {
        mArrayAdapter = EntryListAdapter(this)
        listView.adapter = mArrayAdapter
    }

    // ListerActivity
    public override fun onListsLoaded() {
        super.onListsLoaded()
        Log.d(TAG, "onListsLoaded list $list")
        list.notifyChangeListeners()
        var t = list.text
        if (t == null) t = getString(R.string.app_name)
        supportActionBar!!.title = t
    }

    override fun updateDisplay() {
        // Could simplify to runOnUiThread { notifyAdapter() }
        list.notifyChangeListeners()
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
            if (list.getFlag(EntryList.displaySorted)) {
                dl.sortWith(object : Comparator<EntryListItem> {
                    override fun compare(o1: EntryListItem, o2: EntryListItem): Int {
                        return o1.text!!.compareTo(o2.text!!, ignoreCase = true)
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
        return !list.getFlag(EntryList.displaySorted) && !list.getFlag("moveCheckedToEnd")
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
        if (mMovingItem == null || !mMovingItem!!.isMoveable) return super.dispatchTouchEvent(motionEvent)

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
        val itemIndex = list.indexOf(mMovingItem)

        // Get the index of the moving view in the list of views. It will stay there until
        // the drag is released.
        var viewIndex = listView.childCount - 1
        while (viewIndex >= 0) {
            if ((listView.getChildAt(viewIndex) as EntryListItemView).item === mMovingItem) break
            viewIndex--
        }
        if (viewIndex < 0) throw RuntimeException("Can't find view for item: $mMovingItem")
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
            Log.d(TAG, "Moved " + mMovingItem!!.text + " from " + itemIndex + " to " + moveTo)
            list.remove(mMovingItem!!, false)
            list.put(moveTo, mMovingItem!!)
            list.notifyChangeListeners()
            checkpoint()
        }
        if (motionEvent.action == MotionEvent.ACTION_MOVE) {
            if (mMovingView == null) {
                // Drag is starting
                //Log.d(TAG, "dispatchTouchEvent adding moving view ");
                mMovingView = makeItemView(mMovingItem!!, true)
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
                if (list.getFlag(EntryList.displaySorted)) list.clearFlag(EntryList.displaySorted) else list.setFlag(EntryList.displaySorted)
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
        if (list.getFlag(EntryList.displaySorted)) {
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
            val item = displayOrder[i]
            val itemView = if (convertView == null) makeItemView(item, false) else convertView as EntryListItemView
            itemView.item = item
            itemView.updateView()
            return itemView
        }

        override fun getCount(): Int {
            return list.size()
        }
    }

    companion object {
        private val TAG = EntryListActivity::class.java.simpleName
    }
}