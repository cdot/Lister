package com.cdot.stores;

import android.net.Uri;

public class SAFStore {
    public void SAFStore(Uri uri) {

    }

    public String read() {
        return "";
    }

    public void write(String s) {

    }
}

// Single checklist
    /**
     * Save to a file using standard Java methods
     */
    /*void saveToExternal() {
        String filename = getBackupFolder() + mFileName;
        try {
            File file = new File(filename);
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    throw new IllegalStateException("Couldn't create dir: " + parentFile);
                }
            }
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(this.toJSON().getBytes());
            stream.close();
            Log.d(TAG, "Save list external " + mListName + " " + size());
        } catch (Exception e) {
            Log.d(TAG, "Save list external exception " + e.getMessage());
            e.printStackTrace();
        }
    }*/

    /*void save() {
        try {
            FileOutputStream stream = mContext.openFileOutput(mFileName, Context.MODE_PRIVATE);
            stream.write(this.toJSON().getBytes());
            stream.close();
            Log.d(TAG, "Save list " + mListName + " " + size());
        } catch (Exception e) {
            Log.d(TAG, "Save list exception " + e.getMessage());
            e.printStackTrace();
        }
    }*/


    /*void loadFromExternal() {
        try {
            loadFromStream(new FileInputStream(new File((getBackupFolder() + mFileName))));
            mArrayAdapter.notifyDataSetChanged();
            save();
        } catch (Exception ignored) {
        }
    }*/

    /**
     * Send the list to someone using email. A valid way to share lists!
     */
/*    void sendAsEmail() {
        Log.d(TAG, "Share list");
        // Sending simple data
        Intent intent = new Intent("android.intent.action.SEND_MULTIPLE");
        // Sending it as email
        intent.setType("message/rfc822");
        // Message headers
        intent.putExtra("android.intent.extra.SUBJECT", mContext.getString(R.string.send_list_subject, mListName));
        intent.putExtra("android.intent.extra.TEXT", mContext.getString(R.string.send_list_body, toPlainString()));
        // Attachments. Use FileProvider to expose files externally.
        ArrayList<Uri> arrayList = new ArrayList<>();
        Uri imageUri = FileProvider.getUriForFile(
                mContext,
                "com.cdot.lists.provider",
                mContext.getFileStreamPath(mFileName));
        arrayList.add(imageUri);
        intent.putParcelableArrayListExtra("android.intent.extra.STREAM", arrayList);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Away we go...
        mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.send_list_chooser_headline)));
    }*/


    /**
     * Load from a URI
     * @param uri uri to load from
     * @throws Exception JSONException or IOException
     */
/*    private void loadFromUri(Uri uri) throws Exception {
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File((uri.getPath())));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = mContext.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load external list. Unknown uri scheme: " + uri.getScheme());
        }
        loadFromStream(stream);
    }*/


    /**
     * Load a JSON checklist from an input stream
     * @param stream input stream to read from
     * @throws Exception JSONException or IOException
     */
/*    private void loadFromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        loadFromJSON(sb.toString());
    }*/


    /**
     * Import all the checklists from the given external folder
     */
/*    void loadExternal(Uri uri) {
        // TODO: file picker
        File file = new File(folder);
        if (!file.exists()) {
            Toast.makeText(mContext, R.string.nothing_to_import, Toast.LENGTH_SHORT).show();
            return;
        }
        for (File file2 : file.listFiles()) {
            if (!file2.isDirectory()) {
                try {
                    Log.d(TAG, "import file: " + file2.getAbsolutePath());
                    Checklist checkList = new Checklist(Uri.fromFile(file2), mContext);
                    checkList.changeLocalFile();
                    checkList.save();
                    addChecklist(checkList);
                } catch (Exception e) {
                    Log.d(TAG, "import file failed: " + e.getMessage());
                    Toast.makeText(mContext, mContext.getString(R.string.failed_to_import_file_x, file2.getAbsolutePath()), Toast.LENGTH_LONG).show();
                }
            }
        }
    }*/
/*
    private void showSaveExternalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_save_external_list);
        builder.setMessage(R.string.save_file_prompt);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                mContainer.save();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showLoadExternalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_load_external_list);
        builder.setMessage(R.string.restore_backup_prompt);
        builder.setPositiveButton(R.string.restore, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                mList.loadFromExternal();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    private void showSaveBackupDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.action_save_external_list);
        builder.setMessage(R.string.backup_all_prompt);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                mChecklists.backupToExternalStorage();
                Toast.makeText(ChecklistsActivity.this, R.string.backup_complete, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    */
