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
package com.cdot.lists.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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

import com.cdot.lists.R;
import com.cdot.lists.Settings;
import com.cdot.lists.databinding.ChecklistFragmentBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.ChecklistItem;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.view.ChecklistItemView;
import com.cdot.lists.view.EntryListItemView;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

/**
 * Fragment for managing interactions with a checklist of items
 */
public class ChecklistFragment extends EntryListFragment {
    private static final String TAG = "ChecklistFragment";

    private ChecklistFragmentBinding mBinding;
    
    // helper to avoid frequent casts
    private Checklist mChecklist;
    
    // When in edit mode, sorting and moving checked items is disabled
    public boolean mInEditMode = false;

    @Override // Fragment
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (mList == null)
            return null;

        mBinding = ChecklistFragmentBinding.inflate(inflater, container, false);
        View rootView = mBinding.getRoot();
        setView(mBinding.itemListView, mBinding.checklistFragment);
        setHasOptionsMenu(true);

        mBinding.addItemText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        mBinding.addItemText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                addNewItem();
                return true;
            }
            return false;
        });

        mBinding.addItemText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        enableEditMode(mList.size() == 0);

        return rootView;
    }

    @Override // implements EntryListFragment
    protected String getHelpAsset() {
        return "Checklist";
    }

    @Override // implements EntryListFragment
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistItemView(item, true, this);
    }

    @Override // Fragment
    public void onCreateOptionsMenu(@NonNull Menu menu,  MenuInflater inflater) {
        inflater.inflate(R.menu.checklist, menu);
    }

    @Override // EntryListFragment
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(menuItem);

            case R.id.action_check_all:
                if (mChecklist.checkAll(true)) {
                    mList.notifyChangeListeners();
                    Log.d(TAG, "check all");
                    getMainActivity().save();
                }
                return true;

            case R.id.action_delete_checked:
                int deleted = mChecklist.deleteAllChecked();
                if (deleted > 0) {
                    mChecklist.notifyChangeListeners();
                    Log.d(TAG, "checked deleted");
                    getMainActivity().save();
                    Toast.makeText(getMainActivity(), getString(R.string.items_deleted, deleted), Toast.LENGTH_SHORT).show();
                    if (mList.size() == 0) {
                        enableEditMode(true);
                        mChecklist.notifyChangeListeners();
                        getMainActivity().save();
                    }
                }
                return true;

            case R.id.action_edit:
                enableEditMode(!mInEditMode);
                return true;

            case R.id.action_rename_list:
                AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getMainActivity());
                builder.setTitle(R.string.rename_list);
                builder.setMessage(R.string.enter_new_name_of_list);
                final EditText editText = new EditText(getMainActivity());
                editText.setSingleLine(true);
                editText.setText(mList.getText());
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    Log.d(TAG, "list renamed");
                    mList.setText(editText.getText().toString());
                    getMainActivity().notifyListsListeners();
                    getMainActivity().save();
                    //Objects.requireNonNull(getSupportActionBar()).setTitle(mChecklist.getText());
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;

            case R.id.action_uncheck_all:
                if (mChecklist.checkAll(false)) {
                    Log.d(TAG, "uncheck all");
                    mList.notifyChangeListeners();
                    getMainActivity().save();
                }
                return true;

            case R.id.action_undo_delete:
                int undone = mChecklist.undoRemove();
                mChecklist.notifyChangeListeners();
                Log.d(TAG, "delete undone");
                getMainActivity().save();
                if (undone == 0)
                    Toast.makeText(getMainActivity(), R.string.no_deleted_items, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(getMainActivity(), getString(R.string.items_restored, undone), Toast.LENGTH_SHORT).show();
                return true;

            case R.id.action_save_list_as:
                exportChecklist();
                return true;

            case R.id.action_settings:
                getMainActivity().pushFragment(new SettingsFragment(false));
                return true;
        }
    }

    @Override // EntryListFragment
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_edit);
        if (mInEditMode) {
            menuItem.setIcon(R.drawable.ic_action_item_add_off);
            menuItem.setTitle(R.string.action_item_add_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_item_add_on);
            menuItem.setTitle(R.string.action_item_add_on);
        }

        menu.findItem(R.id.action_settings).setEnabled(!mInEditMode);
        menu.findItem(R.id.action_check_all).setEnabled(((Checklist) mList).getCheckedCount() < mList.size());
        menu.findItem(R.id.action_uncheck_all).setEnabled(((Checklist) mList).getCheckedCount() > 0);
        menu.findItem(R.id.action_undo_delete).setEnabled(mList.getRemoveCount() > 0);
        menu.findItem(R.id.action_delete_checked).setEnabled(((Checklist) mList).getCheckedCount() > 0);

        super.onPrepareOptionsMenu(menu);
    }

    @Override // EntryListFragment
    protected List<EntryListItem> getDisplayOrder() {
        if (mInEditMode)
            return mList.getData();

        List<EntryListItem> list = super.getDisplayOrder(); // get sorted list
        if (Settings.getBool(Settings.showCheckedAtEnd)) {
            int top = list.size();
            int i = 0;
            while (i < top) {
                ChecklistItem item = (ChecklistItem) list.get(i);
                if (item.isDone()) {
                    list.add(list.remove(i));
                    top--;
                } else
                    i++;
            }
        }
        return list;
    }

    /**
     * Construct a fragment to manage the given checklist
     * @param list the list to manage
     */
    public ChecklistFragment(Checklist list) {
        mList = mChecklist = list;
    }

    /**
     * Turn editing "mode" on/off
     *
     * @param isOn new state
     */
    private void enableEditMode(boolean isOn) {
        mInEditMode = isOn;
        notifyAdapter();
        getMainActivity().invalidateOptionsMenu();

        View nic = mBinding.newItemContainer;

        // Show/hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getMainActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (mInEditMode) {
            nic.setVisibility(View.VISIBLE);
            mBinding.addItemText.setFocusable(true);
            mBinding.addItemText.setFocusableInTouchMode(true);
            mBinding.addItemText.requestFocus();
            inputMethodManager.showSoftInput(mBinding.addItemText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            nic.setVisibility(View.INVISIBLE);
            View currentFocus = getMainActivity().getCurrentFocus();
            if (currentFocus == null)
                currentFocus = mBinding.addItemText;
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    /**
     * Handle adding an item from the typing area
     */
    private void addNewItem() {
        String text = mBinding.addItemText.getText().toString();
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
        ChecklistItem item = new ChecklistItem(mChecklist, str, false);
        mList.add(item);
        Log.d(TAG, "item added");
        mList.notifyChangeListeners();
        mBinding.addItemText.setText("");
        mBinding.itemListView.smoothScrollToPosition(getDisplayOrder().indexOf(item));
        getMainActivity().save();
    }

    /**
     * Prompt for similar item already in the list
     *
     * @param proposed the text of the proposed item
     * @param similar  the text of a similar item already in the list
     */
    private void promptSimilarItem(final String proposed, String similar) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getMainActivity());
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getMainActivity());
        builder.setTitle(R.string.export_format);
        final Spinner picker = new Spinner(getMainActivity());
        // Helper for indexing the array of share formats, must be final for inner class access
        final int[] mPlace = new int[1];
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getMainActivity(),
                android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.share_format_description));
        mPlace[0] = 0;
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

        final String listName = mList.getText();

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
                File sendFile = new File(getMainActivity().getExternalFilesDir("send"), fileName);
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
                String authRoot = getMainActivity().getApplicationContext().getPackageName().replace(".debug", "");
                Uri uri = FileProvider.getUriForFile(getMainActivity(), authRoot + ".provider", sendFile);
                intent.putExtra(Intent.EXTRA_STREAM, uri);

                // Fire off the intent
                startActivity(Intent.createChooser(intent, fileName));

            } catch (Exception e) {
                String mess = getString(R.string.failed_export, e.getMessage());
                Log.d(TAG, mess);
                Toast.makeText(getMainActivity(), mess, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
}
