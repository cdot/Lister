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
package com.cdot.lists;

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
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.cdot.lists.databinding.ChecklistActivityBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.ChecklistItem;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.preferences.PreferencesActivity;
import com.cdot.lists.view.ChecklistItemView;
import com.cdot.lists.view.EntryListItemView;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

/**
 * Activity for managing interactions with a checklist of items
 */
public class ChecklistActivity extends EntryListActivity {
    private static final String TAG = ChecklistActivity.class.getSimpleName();
    // When in edit mode, sorting and moving checked items is disabled
    public boolean mInEditMode = false;
    // helper to avoid frequent casts
    private Checklist mChecklist;
    private ChecklistActivityBinding mBinding;

    public ChecklistActivity() {
    }

    @Override
        // EntryListActivity
    EntryList getList() {
        return mChecklist;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(UID_EXTRA, getList().getSessionUID());
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle state) {
        super.onRestoreInstanceState(state);
        if (mChecklist == null) {
            int uid = state.getInt(UID_EXTRA);
            if (uid > 0)
                mChecklist = (Checklist) getLister().getLists().findBySessionUID(uid);
        }
    }

    @Override // AppCompatActivity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        int uid = intent.getIntExtra(UID_EXTRA, -1);
        EntryList lists = getLister().getLists();
        mChecklist = (Checklist) lists.findBySessionUID(uid);

        mBinding = ChecklistActivityBinding.inflate(getLayoutInflater());

        makeAdapter(mBinding.itemListView);

        mBinding.addItemET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        mBinding.addItemET.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                addNewItem();
                return true;
            }
            return false;
        });

        mBinding.addItemET.setImeOptions(EditorInfo.IME_ACTION_DONE);

        enableEditMode(getList().size() == 0);

        setContentView(mBinding.getRoot());
    }

    @Override // implements EntryListActivity
    protected String getHelpAsset() {
        return "Checklist";
    }

    @Override // implements EntryListActivity
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistItemView(item, true, this);
    }

    @Override // Activity
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.checklist, menu);
        return true;
    }

    @Override // EntryListActivity
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        super.onPrepareOptionsMenu(menu);

        MenuItem it = menu.findItem(R.id.action_edit);

        if (mInEditMode) {
            it.setIcon(R.drawable.ic_action_item_add_off);
            it.setTitle(R.string.action_item_add_off);
        } else {
            it.setIcon(R.drawable.ic_action_item_add_on);
            it.setTitle(R.string.action_item_add_on);
        }

        menu.findItem(R.id.action_settings).setEnabled(!mInEditMode);
        menu.findItem(R.id.action_check_all).setEnabled(((Checklist) getList()).getCheckedCount() < getList().size());
        menu.findItem(R.id.action_uncheck_all).setEnabled(((Checklist) getList()).getCheckedCount() > 0);
        menu.findItem(R.id.action_undo_delete).setEnabled(getList().getRemoveCount() > 0);
        menu.findItem(R.id.action_delete_checked).setEnabled(((Checklist) getList()).getCheckedCount() > 0);

        return true;
    }

    @Override // EntryListActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        // Beware dispatchTouchEvent stealing events
        Log.d(TAG, "onOptionsItemSelected");
        if (super.onOptionsItemSelected(menuItem))
            return true;

        int it = menuItem.getItemId();

        if (it == R.id.action_check_all) {
            if (mChecklist.checkAll(true)) {
                getList().notifyChangeListeners();
                Log.d(TAG, "check all");
                checkpoint();
            }

        } else if (it == R.id.action_delete_checked) {
            int deleted = mChecklist.deleteAllChecked();
            if (deleted > 0) {
                mChecklist.notifyChangeListeners();
                Log.d(TAG, "checked deleted");
                checkpoint();
                Toast.makeText(this, getString(R.string.items_deleted, deleted), Toast.LENGTH_SHORT).show();
                if (getList().size() == 0) {
                    enableEditMode(true);
                    mChecklist.notifyChangeListeners();
                    checkpoint();
                }
            }

        } else if (it == R.id.action_edit)
            enableEditMode(!mInEditMode);

        else if (it == R.id.action_rename_list) {
            AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle(R.string.rename_list);
            builder.setMessage(R.string.enter_new_name_of_list);
            final EditText editText = new EditText(this);
            editText.setSingleLine(true);
            editText.setText(getList().getText());
            builder.setView(editText);
            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                Log.d(TAG, "list renamed");
                getList().setText(editText.getText().toString());
                getLister().notifyListsListeners();
                checkpoint();
                //Objects.requireNonNull(getSupportActionBar()).setTitle(mChecklist.getText());
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();

        } else if (it == R.id.action_uncheck_all) {
            if (mChecklist.checkAll(false)) {
                Log.d(TAG, "uncheck all");
                getList().notifyChangeListeners();
                checkpoint();
            }

        } else if (it == R.id.action_undo_delete) {
            int undone = mChecklist.undoRemove();
            if (undone == 0)
                Toast.makeText(this, R.string.no_deleted_items, Toast.LENGTH_SHORT).show();
            else {
                mChecklist.notifyChangeListeners();
                Log.d(TAG, "delete undone");
                checkpoint();
                Toast.makeText(this, getString(R.string.items_restored, undone), Toast.LENGTH_SHORT).show();
            }

        } else if (it == R.id.action_save_list_as)
            exportChecklist();

        else if (it == R.id.action_settings) {
            Intent intent = new Intent(this, PreferencesActivity.class);
            intent.putExtra(UID_EXTRA, mChecklist.getSessionUID());
            startActivityForResult(intent, REQUEST_PREFERENCES);

        } else
            return super.onOptionsItemSelected(menuItem);

        return true;
    }

    @Override // EntryListActivity
    public boolean canManualSort() {
        return !mChecklist.getFlag(Checklist.moveCheckedItemsToEnd) && super.canManualSort();
    }

    @Override // EntryListActivity
    protected List<EntryListItem> getDisplayOrder() {
        if (mInEditMode)
            return getList().getData();

        List<EntryListItem> list = super.getDisplayOrder(); // get sorted list
        if (mChecklist.getFlag(Checklist.moveCheckedItemsToEnd)) {
            int top = list.size();
            int i = 0;
            while (i < top) {
                ChecklistItem item = (ChecklistItem) list.get(i);
                if (item.getFlag(ChecklistItem.isDone)) {
                    list.add(list.remove(i));
                    top--;
                } else
                    i++;
            }
        }
        return list;
    }

    /**
     * Turn editing "mode" on/off
     *
     * @param isOn new state
     */
    private void enableEditMode(boolean isOn) {
        mInEditMode = isOn;
        notifyAdapter();
        invalidateOptionsMenu();

        // Show/hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (mInEditMode) {
            mBinding.addItemET.setVisibility(View.VISIBLE);
            mBinding.addItemET.setFocusable(true);
            mBinding.addItemET.setFocusableInTouchMode(true);
            mBinding.addItemET.requestFocus();
            inputMethodManager.showSoftInput(mBinding.addItemET, InputMethodManager.SHOW_IMPLICIT);
        } else {
            mBinding.addItemET.setVisibility(View.INVISIBLE);
            View currentFocus = getCurrentFocus();
            if (currentFocus == null)
                currentFocus = mBinding.addItemET;
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    /**
     * Handle adding an item from the typing area
     */
    private void addNewItem() {
        String text = mBinding.addItemET.getText().toString();
        if (text.trim().length() == 0)
            return;
        EntryListItem find = getList().findByText(text, false);
        if (find == null || !mChecklist.getFlag(EntryList.warnAboutDuplicates))
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
        ChecklistItem item = new ChecklistItem(mChecklist, str, false);
        getList().add(item);
        Log.d(TAG, "item added");
        getList().notifyChangeListeners();
        mBinding.addItemET.setText("");
        mBinding.itemListView.smoothScrollToPosition(getDisplayOrder().indexOf(item));
        checkpoint();
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
    private void exportChecklist() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.export_format);
        final Spinner picker = new Spinner(this);
        // Helper for indexing the array of share formats, must be final for inner class access
        final int[] mPlace = new int[1];
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.share_format_description));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                mPlace[0] = position;
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        builder.setView(picker);

        final String listName = getList().getText();

        builder.setPositiveButton(R.string.ok, (dialog, id) -> {
            final Intent intent = new Intent(Intent.ACTION_SEND);

            intent.putExtra(Intent.EXTRA_TITLE, listName); // Dialog title

            String mimeType = getResources().getStringArray(R.array.share_format_mimetype)[mPlace[0]];
            intent.setType(mimeType);

            String ext = getResources().getStringArray(R.array.share_format_mimeext)[mPlace[0]];
            String fileName = listName.replaceAll("[/\u0000]", "_") + ext;
            // WTF! The EXTRA_SUBJECT is used as the document title for Drive saves!
            intent.putExtra(Intent.EXTRA_SUBJECT, fileName);

            // text body e.g. for email
            String text = mChecklist.toPlainString("");
            intent.putExtra(Intent.EXTRA_TEXT, text);

            try {
                // Write a local file for the attachment
                File sendFile = new File(getExternalFilesDir("send"), fileName);
                Writer w = new FileWriter(sendFile);
                switch (mimeType) {
                    case "text/plain":
                        // Re-write the text body in the attachment
                        w.write(text);
                        break;

                    case "application/json":
                        w.write(mChecklist.toJSON().toString());
                        break;

                    case "text/csv":
                        CSVWriter csvw = new CSVWriter(w);
                        mChecklist.toCSV(csvw);
                        break;

                    default:
                        throw new Exception("Unrecognised share format");
                }
                w.close();

                // Expose the local file using a URI from the FileProvider, and add the URI to the intent
                // See https://medium.com/@ali.muzaffar/what-is-android-os-fileuriexposedexception-and-what-you-can-do-about-it-70b9eb17c6d0
                String authRoot = getPackageName().replace(".debug", "");
                Uri uri = FileProvider.getUriForFile(this, authRoot + ".provider", sendFile);
                intent.putExtra(Intent.EXTRA_STREAM, uri);

                // Fire off the intent
                startActivity(Intent.createChooser(intent, fileName));

            } catch (Exception e) {
                String mess = getString(R.string.failed_export, e.getMessage());
                Log.d(TAG, mess);
                Toast.makeText(this, mess, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
}
