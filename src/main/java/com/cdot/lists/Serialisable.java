package com.cdot.lists;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Objects;

abstract class Serialisable {
    private final String TAG = "Serialisable";

    protected Context mContext;

    Serialisable(Context cxt) {
        mContext = cxt;
    }

    Serialisable(InputStream stream, Context cxt) throws Exception {
        this(cxt);
        fromStream(stream);
    }

    Serialisable(Uri uri, Context cxt) throws Exception {
        this(cxt);
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File((uri.getPath())));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = mContext.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
        }
        fromStream(stream);
    }

    /**
     * Get the JSON object that represents the content of this object
     * @return a JOSNObject or JSONArray
     * @throws JSONException if it fails
     */
    abstract Object toJSON() throws JSONException;

    /**
     * Perform an asynchronous save to a URI
     * @param uri
     */
    void saveToUri(final Uri uri) {
        // Launch a thread to do this save, so we don't block the ui thread
        Log.d(TAG, "Saving to " + uri);
        final byte[] data;
        try {
            data = toJSON().toString().getBytes();
        } catch (JSONException je) {
            throw new Error("JSON exception " + je.getMessage());
        }
        new Thread(new Runnable() {
            public void run() {
                OutputStream stream;
                try {
                    String scheme = uri.getScheme();
                    if (Objects.equals(scheme, ContentResolver.SCHEME_FILE)) {
                        String path = uri.getPath();
                        stream = new FileOutputStream(new File(path));
                    } else if (Objects.equals(scheme, ContentResolver.SCHEME_CONTENT))
                        stream = mContext.getContentResolver().openOutputStream(uri);
                    else
                        throw new IOException("Unknown uri scheme: " + uri.getScheme());
                    if (stream == null)
                        throw new IOException("Stream open failed");
                    stream.write(data);
                    stream.close();
                    Log.d(TAG, "Saved to " + uri);
                } catch (IOException ioe) {
                    final String mess = ioe.getMessage();
                    ((Activity) mContext).runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(mContext, "Exception while saving to Uri " + mess, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Read a JSON object to get the content for this object
     * @param json a JSONObject or JSONArray
     * @throws JSONException if it fails
     */
    abstract void fromJSON(Object json) throws JSONException;

    /**
     * Load the object from JSON read from the stream
     *
     * @param stream source of the JSON
     * @throws Exception IOException or JSONException
     */
    void fromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        fromJSON(new JSONArray(sb.toString()));
    }
}
