/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.cdot.lists.databinding.ChecklistsActivityBinding;

/**
 * Activity that displays a list of checklists. The checklists are stored in a paired Checklists
 * object.
 */
public class ChecklistsActivity extends EntryListActivity {
    private static final String TAG = "ChecklistsActivity";

    // Activity request codes
    private static final int REQUEST_IMPORT_LIST = 3;
    private static final int REQUEST_PREFERENCES = 4;

    // The actual checklists
    private Checklists mChecklists;

    private ChecklistsActivityBinding mBinding;

    @Override // EntryListActivity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Settings.setContext(this);

        mChecklists = new Checklists(this, true);
        setList(mChecklists);

        mBinding = ChecklistsActivityBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        setSupportActionBar(mBinding.checklistsToolbar);

        ListView listView = mBinding.listList;
        listView.setAdapter(mChecklists.mArrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
                launchChecklistActivity(i);
            }
        });
        /*listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int index, long j) {
                PopupMenu popupMenu = new PopupMenu(ChecklistsActivity.this, view);
                popupMenu.inflate(R.menu.checklists_popup);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_copy:
                                mChecklists.cloneListAt(index);
                                loadLists();
                                return true;
                            case R.id.action_delete:
                                showDeleteConfirmDialog(index);
                                return true;
                            case R.id.action_rename:
                                showRenameDialog(index);
                                loadLists();
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                popupMenu.show();
                return true;
            }
        });*/

        if (Settings.getBool(Settings.openLatest)) {
            String currentList = Settings.getString(Settings.currentList);
            if (currentList != null) {
                int idx = mChecklists.find(currentList, true);
                if (idx >= 0) {
                    Log.d(TAG, "launching " + currentList);
                    launchChecklistActivity(idx);
                    return;
                }
            }
            Log.d(TAG, "no list to launch " + currentList);
        }

    }

    @Override // EntryListActivity
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistsItemView(item, true, this);
    }

    @Override // EntryListActivity
    protected ListView getListView() {
        return mBinding.listList;
    }

    @Override // EntryListActivity
    protected RelativeLayout getListLayout() {
        return mBinding.checklistsActivity;
    }

    @Override // AppCompatActivity
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        loadLists();
    }

    @Override // AppCompatActivity
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklists, menu);
        return true;
    }

    @Override // AppCompatActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.action_help:
                Intent hIntent = new Intent(this, HelpActivity.class);
                hIntent.putExtra("page", "Checklists");
                startActivity(hIntent);
                return true;

            case R.id.action_import_list:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQUEST_IMPORT_LIST);
                return true;

            case R.id.action_new_list:
                showNewListNameDialog();
                return true;

            default:
                return super.onOptionsItemSelected(menuItem);

            case R.id.action_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_PREFERENCES);
                return true;
        }
    }

    /**
     * Handle results from activities started with startActivityForResult
     *
     * @param requestCode startActivityForResult
     * @param resultCode  did it work?
     * @param resultData  and what's the data?
     */
    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMPORT_LIST && resultData != null) {
                try {
                    Uri uri = resultData.getData();
                    if (uri == null)
                        return;
                    Checklist checklist = new Checklist(uri, mChecklists, this);
                    int idx = mChecklists.add(checklist);
                    Log.d(TAG, "import list: " + checklist.getName());
                    launchChecklistActivity(idx);
                } catch (Exception e) {
                    Log.d(TAG, "import failed to create list. " + e.getMessage());
                }
            } else if (requestCode == REQUEST_PREFERENCES)
                // An OK result from the settings activity means list source has changed
                loadLists();
        }
    }

    /**
     * Launch the checklist reading content from the given private filename
     *
     * @param idx index of Checklist to open
     */
    private void launchChecklistActivity(int idx) {
        Intent intent = new Intent(this, ChecklistActivity.class);
        intent.putExtra("index", idx);
        intent.putExtra("name", ((Checklist)mChecklists.get(idx)).getName());
        startActivity(intent);
    }

    // Load the list of known lists
    private void loadLists() {
        mChecklists.load();
        if (mChecklists.size() == 0)
            Toast.makeText(this, R.string.no_lists, Toast.LENGTH_LONG).show();
    }

    private void showNewListNameDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.create_new_list);
        builder.setMessage(R.string.enter_name_of_new_list);
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        builder.setView(editText);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                String listname = editText.getText().toString();
                int idx = mChecklists.add(new Checklist(listname, mChecklists, ChecklistsActivity.this));
                launchChecklistActivity(idx);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
        editText.post(new Runnable() {
            public void run() {
                editText.setFocusable(true);
                editText.setFocusableInTouchMode(true);
                editText.requestFocus();
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editText, 1);
            }
        });
    }
}
