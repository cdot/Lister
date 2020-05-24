package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

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
import android.widget.TextView;

import com.cdot.lists.databinding.ChecklistsActivityBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Activity that displays a list of checklists
 */
public class ChecklistsActivity extends AppCompatActivity {
    private static final String TAG = "ChecklistsActivity";


    // Activity request codes
    private static final int REQUEST_IMPORT_LIST = 3;
    private static final int REQUEST_SAVE_FILE = 4;

    // The actual checklists
    private Checklists mChecklists;

    private ChecklistsActivityBinding mBinding;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Settings.setContext(this);

        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        //getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        mChecklists = new Checklists(this, true);

        mBinding = ChecklistsActivityBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);
        setSupportActionBar(mBinding.checklistsToolbar);

        ListView listView = mBinding.ListList;
        listView.setAdapter(mChecklists.mArrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
                launchChecklistActivity(mChecklists.get(i).mListName);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int index, long j) {
                PopupMenu popupMenu = new PopupMenu(ChecklistsActivity.this, view);
                popupMenu.inflate(R.menu.checklists_popup);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_copy:
                                mChecklists.cloneListAtIndex(index);
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
        });

        if (Settings.getBool(Settings.openLatestListAtStartup)) {
            String currentList = Settings.getString(Settings.currentList);
            if (currentList != null && mChecklists.findListByName(currentList) != null) {
                Log.d(TAG, "launching " + currentList);
                launchChecklistActivity(currentList);
            } else
                Log.d(TAG, "no list to launch " + currentList);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        loadLists();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklists, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.action_help:
                startActivity(new Intent(this, HelpActivity.class));
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

            case R.id.action_rename:
            case R.id.action_rename_list:
            default:
                return super.onOptionsItemSelected(menuItem);

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
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
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        switch (requestCode) {
            case REQUEST_IMPORT_LIST:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    try {
                        Uri uri = resultData.getData();
                        if (uri == null)
                            return;
                        InputStream stream;
                        if (Objects.equals(uri.getScheme(), "file")) {
                            stream = new FileInputStream(new File((uri.getPath())));
                        } else if (Objects.equals(uri.getScheme(), "content")) {
                            stream = getContentResolver().openInputStream(uri);
                        } else {
                            throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                        }
                        Checklist checklist = mChecklists.createList(stream);
                        Log.d(TAG, "import list: " + checklist.mListName);
                        launchChecklistActivity(checklist.mListName);
                    } catch (Exception e) {
                        Log.d(TAG, "import failed to create list. " + e.getMessage());
                    }
                }
                break;
        }
    }

    /**
     * Launch the checklist reading content from the given private filename
     *
     * @param listName Checklist to open
     */
    private void launchChecklistActivity(String listName) {
        Intent intent = new Intent(this, ChecklistActivity.class);
        intent.putExtra("list", listName);
        startActivity(intent);
    }

    // Load the list of known lists
    private void loadLists() {
        mChecklists.load();
        TextView itv = mBinding.InfoText;
        if (mChecklists.size() == 0) {
            itv.setText(R.string.no_lists);
            itv.setVisibility(View.VISIBLE);
            return;
        }
        itv.setVisibility(View.GONE);
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
                Checklist checkList = mChecklists.createList(listname);
                launchChecklistActivity(checkList.mListName);
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

    private void showDeleteConfirmDialog(final int index) {
        String string = getResources().getString(R.string.action_delete_list);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(string + "?");
        builder.setMessage(string + " \"" + mChecklists.get(index).mListName + "\"?");
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which_button) {
                mChecklists.removeListAtIndex(index);
                loadLists(); // needed to rest the adapter list
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showRenameDialog(int i) {
        final Checklist checkList = mChecklists.get(i);
        String listname = mChecklists.get(i).mListName;
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle(R.string.action_rename_list);
            builder.setMessage(R.string.enter_new_name_of_list);
            final EditText editText = new EditText(this);
            editText.setText(listname);
            builder.setView(editText);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int which_button) {
                    checkList.mListName = editText.getText().toString();
                    loadLists();
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
        } catch (Exception e) {
            Log.d(TAG, "Rename list exception: " + e.getMessage());
        }
    }
}
