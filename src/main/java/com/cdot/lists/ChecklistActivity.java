/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.view.accessibility.AccessibilityEventCompat;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Objects;

/**
 * Activity for a checklist of items
 */
public class ChecklistActivity extends EntryListActivity {
    private static final String TAG = "ChecklistActivity";

    static final String EXTRA_UID = "uid";
    static final String EXTRA_NAME = "name";

    private static final int REQUEST_EXPORT_LIST = 4;

    private Checklists mChecklists;

    private EditText mAddItemText;

    /**
     * Construct the checklist (index passed in the Intent)
     */
    @Override // AppCompatActivity
    protected void onCreate(Bundle bundle) {
        Log.d(TAG, "onCreate");
        super.onCreate(bundle);

        setLayout(R.layout.checklist_activity);

        mAddItemText = findViewById(R.id.add_item_text);
        mAddItemText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        mAddItemText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                addNewItem();
                return true;
            }
            return false;
        });

        mAddItemText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // mChecklists is just a context placeholder here
        mChecklists = new Checklists(this);
        mChecklists.load(this);

        Checklist list = null;
        long uid = getIntent().getLongExtra(EXTRA_UID, Settings.INVALID_UID);
        if (uid != Settings.INVALID_UID)
            list = (Checklist) mChecklists.findByUID(uid);
        else {
            String listName = getIntent().getStringExtra(EXTRA_NAME);
            if (listName != null) {
                list = new Checklist(mChecklists, listName);
                mChecklists.add(list);
            }
        }
        if (list == null) {
            Settings.setUID(Settings.currentList, Settings.INVALID_UID);
            // Abort back to the lists activity
            finish();
            return;
        }
        setList(list);

        Settings.setUID(Settings.currentList, list.getUID());
        Objects.requireNonNull(getSupportActionBar()).setTitle(list.getText());

        enableEditMode(list.size() == 0);
    }

    @Override // EntryListActivity
    protected String getHelpAsset() {
        return "Checklist";
    }

    @Override // EntryListActivity
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistItemView(item, true, this);
    }

    @Override // AppCompatActivity
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_EXPORT_LIST && resultCode == Activity.RESULT_OK && resultData != null)
            mList.saveToUri(resultData.getData(), this);
    }

    @Override // AppCompatActivity
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklist, menu);
        return true;
    }

    private Checklist getList() {
        return (Checklist) mList;
    }

    @Override // EntryListActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(menuItem);
            case R.id.action_check_all:
                getList().checkAll(true);
                return true;
            case R.id.action_delete_checked:
                int deleted = getList().deleteAllChecked();
                if (deleted > 0) {
                    getList().notifyListChanged(true);
                    Toast.makeText(this, getString(R.string.x_items_deleted, deleted), Toast.LENGTH_SHORT).show();
                    if (mList.size() == 0) {
                        enableEditMode(true);
                        getList().notifyListChanged(true);
                        invalidateOptionsMenu();
                    }
                }
                return true;
            case R.id.action_edit:
                enableEditMode(!mList.mInEditMode);
                return true;
            case R.id.action_rename_list:
                AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle(R.string.rename_list);
                builder.setMessage(R.string.enter_new_name_of_list);
                final EditText editText = new EditText(this);
                editText.setSingleLine(true);
                editText.setText(mList.getText());
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    mList.setText(editText.getText().toString());
                    mChecklists.notifyListChanged(true);
                    Objects.requireNonNull(getSupportActionBar()).setTitle(getList().getText());
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;
            case R.id.action_uncheck_all:
                getList().checkAll(false);
                return true;
            case R.id.action_undo_delete:
                int undone = getList().undoRemove();
                getList().notifyListChanged(true);
                if (undone == 0)
                    Toast.makeText(this, R.string.no_deleted_items, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, getString(R.string.x_items_restored, undone), Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_save_list_as:
                exportChecklist();
                return true;
        }
    }

    @Override // EntryListActivity
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_edit);
        if (mList.mInEditMode) {
            menuItem.setIcon(R.drawable.ic_action_item_add_off);
            menuItem.setTitle(R.string.action_item_add_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_item_add_on);
            menuItem.setTitle(R.string.action_item_add_on);
        }
        menuItem = menu.findItem(R.id.action_undo_delete);
        menuItem.setEnabled(((Checklist) mList).mRemoves.size() > 0);
        menuItem = menu.findItem(R.id.action_delete_checked);
        menuItem.setEnabled(((Checklist) mList).getCheckedCount() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override // AppCompatActivity
    public void onAttachedToWindow() {
        if (Settings.getBool(Settings.alwaysShow)) {
            getWindow().addFlags(AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
        }
    }

    /**
     * Turn editing "mode" on/off
     *
     * @param isOn new state
     */
    private void enableEditMode(boolean isOn) {
        mList.setEditMode(isOn);
        View nic = findViewById(R.id.new_item_container);

        // Show/hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (mList.mInEditMode) {
            nic.setVisibility(View.VISIBLE);
            mAddItemText.setFocusable(true);
            mAddItemText.setFocusableInTouchMode(true);
            mAddItemText.requestFocus();
            inputMethodManager.showSoftInput(mAddItemText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            nic.setVisibility(View.INVISIBLE);
            View currentFocus = getCurrentFocus();
            if (currentFocus == null)
                currentFocus = mAddItemText;
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
        invalidateOptionsMenu();
    }

    /**
     * Handle adding an item from the typing area
     */
    private void addNewItem() {
        String text = mAddItemText.getText().toString();
        if (text.trim().length() == 0)
            return;
        EntryListItem find = mList.findByText(text, false);
        if (find == null || !Settings.getBool(Settings.warnAboutDuplicates))
            addItem(text);
        else
            promptSimilarItem(text, find.getText());
    }

    /**
     * Handle adding an item after it's confirmed
     *
     * @param str the text of the item
     */
    private void addItem(String str) {
        ChecklistItem item = new ChecklistItem(getList(), str, false);
        mList.add(item);
        mList.notifyListChanged(true);
        mAddItemText.setText("");
        ListView lv = findViewById(R.id.entry_list_activity_list_view);
        lv.smoothScrollToPosition(mList.indexOfDisplayed(item));
    }

    /**
     * Prompt for similar item already in the list
     *
     * @param proposed the text of the proposed item
     * @param similar  the text of a similar item already in the list
     */
    private void promptSimilarItem(final String proposed, String similar) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.similar_item_already_in_list);
        builder.setMessage(getString(R.string.similar_item_x_already_in_list, similar, proposed));
        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> addItem(proposed));
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    /**
     * Export the checklist in a user-selected format
     */
    private int mPlace;

    private void exportChecklist() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_share_format);
        final Spinner picker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.share_format_description));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                mPlace = position;
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        builder.setView(picker);

        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_TITLE, mList.getText());
        builder.setPositiveButton(R.string.ok, (dialog, id) -> {
            String mimeType = getResources().getStringArray(R.array.share_format_mimetype)[mPlace];
            intent.setType(mimeType);
            String ext = getResources().getStringArray(R.array.share_format_mimeext)[mPlace];
            String fileName = mList.getText().replaceAll("[/\u0000]", "_") + ext;
            // The EXTRA_SUBJECT is used as the document title for Drive saves
            intent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            String text = getList().toPlainString("");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            try {
                File sendFile = new File(getExternalFilesDir("send"), fileName);
                Writer w = new FileWriter(sendFile);
                switch (mimeType) {
                    case "text/plain":
                        w.write(text);
                        break;
                    case "application/json":
                        w.write(getList().toJSON().toString());
                        break;
                    case "text/csv":
                        CSVWriter csvw = new CSVWriter(w);
                        getList().toCSV(csvw);
                        break;
                    default:
                        throw new Exception("Unrecognised share format");
                }
                w.close();
                // See https://medium.com/@ali.muzaffar/what-is-android-os-fileuriexposedexception-and-what-you-can-do-about-it-70b9eb17c6d0
                String authRoot = getApplicationContext().getPackageName().replace(".debug", "");
                Uri uri = FileProvider.getUriForFile(ChecklistActivity.this, authRoot + ".provider", sendFile);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(intent, fileName));
            } catch (Exception e) {
                Log.d(TAG, "Share failed " + e.getMessage());
                Toast.makeText(ChecklistActivity.this, getString(R.string.share_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
}
