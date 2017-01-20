package com.kolapsis.rtdb_android.providers;

import android.content.*;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.helpers.DataHelper;
import com.kolapsis.rtdb_android.helpers.DatabaseHelper;
import com.kolapsis.rtdb_android.helpers.Entity;
import com.kolapsis.rtdb_android.utils.CloseUtils;

import java.io.File;
import java.io.FileNotFoundException;

public class CategoryProvider extends ContentProvider {

    public static final String TAG 				= Constants.APP_NAME + ".CategoryProvider";

    public static final String AUTHORITY 		= Constants.AUTHORITY + ".Category";

    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(AUTHORITY, DataHelper.Category.TABLE, DataHelper.Category.DIRECTORY);
        sUriMatcher.addURI(AUTHORITY, DataHelper.Category.TABLE + "/#", DataHelper.Category.ITEM);

    }

    private DatabaseHelper sql;

    @Override
    public boolean onCreate() {
        Log.v(TAG, "onCreate()");
        sql = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // getDataValue(uri);
        if (!isItemUri(uri)) throw new IllegalArgumentException ("URI invalid. Use an id-based URI only.");
        return openFileHelper(uri, mode);
    }

    @Override
    public String getType(Uri uri) {
        switch(sUriMatcher.match(uri)) {
            case DataHelper.Category.DIRECTORY:
                return DataHelper.Category.CONTENT_TYPE;
            case DataHelper.Category.ITEM:
                return DataHelper.Category.MIME_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (!isTableUri(uri)) throw new IllegalArgumentException("Unknown URI " + uri);
        else if (values == null) throw new IllegalArgumentException("ContentValues coun't be null");
        SQLiteDatabase db = sql.getWritableDatabase();
        long rowId = db.insert(getTable(uri), null, values);
        if (rowId > 0) {
            Uri newUri = ContentUris.withAppendedId(getContentUri(uri), rowId);
            boolean syncToNetwork = !callerIsSyncAdapter(uri);
            //Log.d(TAG, "insert: " + newUri + ", syncToNetwork: " + syncToNetwork);
            getContext().getContentResolver().notifyChange(uri, null, syncToNetwork);
            return newUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = sql.getWritableDatabase();
        if (!isTableUri(uri) && !isItemUri(uri)) throw new IllegalArgumentException("Unknown URI " + uri);
        else if (values == null) throw new IllegalArgumentException("ContentValues coun't be null");
        if (isItemUri(uri)) where = "_id = " + uri.getLastPathSegment();
        int count = db.update(getTable(uri), values, where, whereArgs);
        boolean syncToNetwork = !callerIsSyncAdapter(uri);
        //Log.d(TAG, "update: " + count + ", syncToNetwork: " + syncToNetwork);
        getContext().getContentResolver().notifyChange(uri, null, syncToNetwork);
        return count;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        if (!isTableUri(uri) && !isItemUri(uri)) throw new IllegalArgumentException("Unknown URI " + uri);
        if (isItemUri(uri)) where = "_id = " + uri.getLastPathSegment();
        SQLiteDatabase db = sql.getWritableDatabase();
        boolean callerIsSyncAdapter = callerIsSyncAdapter(uri);
        int count;
        if (callerIsSyncAdapter) {
            if (isItemUri(uri)) deleteFile(uri);
            count = db.delete(getTable(uri), where, whereArgs);
        } else {
            ContentValues values = new ContentValues();
            values.put(Entity.BaseColumns.DELETED, 1);
            count = db.update(getTable(uri), values, where, whereArgs);
        }
        //Log.d(TAG, "delete: " + count + ", callerIsSyncAdapter: " + callerIsSyncAdapter);
        getContext().getContentResolver().notifyChange(uri, null, !callerIsSyncAdapter);
        return count;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String where, String[] whereArgs, String sortOrder) {
        if (isItemUri(uri)) where = "_id = " + uri.getLastPathSegment();
        if (projection == null) projection = getProjection(uri);
        SQLiteDatabase db = sql.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query(getTable(uri), projection, where, whereArgs, null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
        } catch (SQLiteException e) {}
        return c;
    }

    private String getDataValue(Uri uri) {
        if (!isItemUri(uri)) return null;
        // Log.v(TAG, "find file path for: "+uri);
        SQLiteDatabase db = sql.getReadableDatabase();
        String path = null;
        Cursor c = null;
        try {
            c = db.query(getTable(uri), new String[]{"_data"}, "_id = ?", new String[]{uri.getLastPathSegment()}, null, null, null);
            // Log.v(TAG, "row:" + c.getCount() + ", column: " + c.getColumnCount());
            if (c.moveToFirst() && c.getColumnCount() == 1) {
                path = c.getString(0);
                if (TextUtils.isEmpty(path)) path = null;
            }
        } finally {
            CloseUtils.closeQuietly(c);
        }
        // Log.v(TAG, "-> path: " + path);
        return path;
    }

    private void deleteFile(Uri uri) {
        if (!isItemUri(uri)) return;
        // Log.v(TAG, "delete file for: "+uri);
        boolean deleted = false;
        String path = getDataValue(uri);
        File file = new File(path);
        if (file.exists()) deleted = file.delete();
        // Log.v(TAG, "-> deleted: "+deleted);
        return;
    }

    private static boolean callerIsSyncAdapter(Uri uri) {
        return Boolean.parseBoolean(uri.getQueryParameter(Constants.CALLER_IS_SYNCADAPTER));
    }

    private boolean isTableUri(Uri uri) {
        switch(sUriMatcher.match(uri)) {
            case DataHelper.Category.DIRECTORY:
                return true;
            default:
                return false;
        }
    }
    private boolean isItemUri(Uri uri) {
        switch(sUriMatcher.match(uri)) {
            case DataHelper.Category.ITEM:
                return true;
            default:
                return false;
        }
    }

    private String getTable(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case DataHelper.Category.DIRECTORY:
            case DataHelper.Category.ITEM:
                return DataHelper.Category.TABLE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }
    private Uri getContentUri(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case DataHelper.Category.DIRECTORY:
            case DataHelper.Category.ITEM:
                return DataHelper.Category.CONTENT_URI;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }
    private String[] getProjection(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case DataHelper.Category.DIRECTORY:
            case DataHelper.Category.ITEM:
                return DataHelper.Category.Columns.FULL_PROJECTION;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }
}
