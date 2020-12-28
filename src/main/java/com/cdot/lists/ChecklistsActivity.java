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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.cdot.lists.databinding.ChecklistsActivityBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.preferences.PreferencesActivity;
import com.cdot.lists.view.ChecklistsItemView;
import com.cdot.lists.view.EntryListItemView;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

/**
 * Activity that displays a list of checklists. The checklists are stored in the MainActivity.
 */
public class ChecklistsActivity extends EntryListActivity {
    private static final String TAG = ChecklistsActivity.class.getSimpleName();

    private ChecklistsActivityBinding mBinding;

    public ChecklistsActivity() {
    }

    @Override // EntryListActivity
    EntryList getList() {
        return getLister().getLists();
    }

    @Override // ListerActivity
    protected View getRootView() {
        return mBinding.getRoot();
    }

    @Override // AppCompatActivity
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mBinding = ChecklistsActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
    }

    @Override // EntryListActivity
    protected EntryListItemView makeItemView(EntryListItem item, boolean drag) {
        return new ChecklistsItemView(item, drag, this);
    }

    @Override // EntryListActivity
    protected int getHelpAsset() {
        return R.raw.checklists_help;
    }

    @Override // EntryListActivity
    public void onListChanged(EntryListItem item) {
        super.onListChanged(item);
        int sz = getList().size();
        mBinding.listsMessage.setVisibility(sz == 0 ? View.VISIBLE : View.GONE);
        mBinding.itemListView.setVisibility(sz == 0 ? View.GONE : View.VISIBLE);
    }

    @Override // EntryListActivity
    public void onListsLoaded() {
        makeAdapter();
        super.onListsLoaded();
    }

    @Override // AppCompatActivity
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        //Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.checklists, menu);
        return true;
    }

    @Override // AppCompatActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        //Log.d(TAG, "onOptionsItemSelected");
        if (super.onOptionsItemSelected(menuItem))
            return true;

        int it = menuItem.getItemId();

        if (it == R.id.action_import_lists) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, Lister.REQUEST_IMPORT);

        } else if (it == R.id.action_new_list) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.create_new_list);
            builder.setMessage(R.string.enter_name_of_new_list);
            final EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editText.setSingleLine(true);
            builder.setView(editText);
            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                addNewList(editText.getText().toString());
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
            editText.post(() -> {
                editText.setFocusable(true);
                editText.setFocusableInTouchMode(true);
                editText.requestFocus();
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            });

        } else if (it == R.id.action_export_all_lists) {
            exportChecklists();

        } else if (it == R.id.action_settings) {
            Intent sint = new Intent(this, PreferencesActivity.class);
            startActivityForResult(sint, REQUEST_PREFERENCES);

        } else
            return super.onOptionsItemSelected(menuItem);

        return true;
    }

    private static final int LISTS_CHANGED = 0xC04EFE;

    private final Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == LISTS_CHANGED) {
                invalidateOptionsMenu(); // update menu items
                getList().notifyChangeListeners();
            }
        }
    };

    @Override // EntryListActivity
    public ListView getListView() {
        return mBinding.itemListView;
    }

    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        //Log.d(TAG, "onActivityResult");
        if (resultCode != Activity.RESULT_OK || resultData == null)
            return;

        if (requestCode == Lister.REQUEST_IMPORT) {
            if (mArrayAdapter == null)
                // need to create the adapter if lists are empty and there isn't one yet
                makeAdapter();
            getLister().importList(resultData.getData(), this,
                    imports -> {
                        StringBuilder report = new StringBuilder();
                        for (EntryListItem eli : (List<EntryListItem>)imports) {
                            Log.d(TAG, "Imported list: " + eli.getText());
                            if (report.length() > 0)
                                report.append(", ");
                            report.append("'").append(eli.getText()).append("'");
                        }
                        report(getString(R.string.import_report, report), Snackbar.LENGTH_INDEFINITE);
                        Message msg = mMessageHandler.obtainMessage(LISTS_CHANGED);
                        mMessageHandler.sendMessage(msg);
                    },
                    code -> report(code, Snackbar.LENGTH_LONG));
        } else if (requestCode == ListerActivity.REQUEST_CHANGE_STORE || requestCode == ListerActivity.REQUEST_CREATE_STORE)
            getLister().handleChangeStore(this, resultData,
                    lists -> ensureListsLoaded(),
                    code -> report(code, Snackbar.LENGTH_SHORT));
    }

    /**
     * Handle adding an item from the typing area
     */
    private void addNewList(String text) {
        if (text.trim().length() == 0)
            return;
        EntryListItem find = getList().findByText(text, false);
        if (find == null || !getLister().getBool(Lister.PREF_WARN_DUPLICATE))
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
        Checklist item = new Checklist(str);
        getList().addChild(item);
        Log.d(TAG, "item " + item + " added to " + getList());
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
     * Export the checklists in JSON format
     */
    private void exportChecklists() {

        final String listName = getList().getText();
        final Intent intent = new Intent(Intent.ACTION_SEND);

        intent.putExtra(Intent.EXTRA_TITLE, listName); // Dialog title

        intent.setType("application/json");

        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType("application/json") ;
        String fileName = listName.replaceAll("[/\u0000]", "_") + ext;
        // WTF! The EXTRA_SUBJECT is used as the document title for Drive saves!
        intent.putExtra(Intent.EXTRA_SUBJECT, fileName);

        // text body e.g. for email
        String text = getList().toPlainString("");
        intent.putExtra(Intent.EXTRA_TEXT, text);

        try {
            // Write a local file for the attachment
            File sendFile = new File(getExternalFilesDir("send"), fileName);
            Writer w = new FileWriter(sendFile);
            w.write(getList().toJSON().toString());
            w.close();

            // Expose the local file using a URI from the FileProvider, and add the URI to the intent
            // See https://medium.com/@ali.muzaffar/what-is-android-os-fileuriexposedexception-and-what-you-can-do-about-it-70b9eb17c6d0
            String authRoot = getPackageName().replace(".debug", "");
            Uri uri = FileProvider.getUriForFile(this, authRoot + ".provider", sendFile);
            intent.putExtra(Intent.EXTRA_STREAM, uri);

            // Fire off the intent
            startActivity(Intent.createChooser(intent, fileName));

        } catch (Exception e) {
            Log.d(TAG, "Export failed " + e.getMessage());
            report(R.string.failed_export, Snackbar.LENGTH_INDEFINITE);
        }
    }
}
