package com.kolapsis.rtdb_android.helpers;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.text.TextUtils;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.providers.CategoryProvider;
import com.kolapsis.rtdb_android.utils.CloseUtils;
import com.kolapsis.rtdb_android.utils.StringUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "WeakerAccess", "RedundantIfStatement", "SimplifiableIfStatement", "SameParameterValue"})
@SuppressLint("StaticFieldLeak")
public class DataHelper {

    public static final String TAG							= Constants.APP_NAME + ".DataHelper";

    private static Context sContext;
    private static ContentResolver sContentResolver;
    private static String sPath;

    public static void setContext(Context ctx) {
        sContext = ctx;
        sContentResolver = sContext.getContentResolver();
        sPath = sContext.getFilesDir().getPath() + "/" + Category.TABLE + "/";
        File dir = new File(sPath);
        if (!dir.exists()) dir.mkdir();
    }

    public static void setContent(Uri uri, Uri cUri) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = sContentResolver.openInputStream(cUri);
            if (inputStream != null) {
                outputStream = sContentResolver.openOutputStream(uri);
                IOUtils.copy(inputStream, outputStream);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
        } finally {
            CloseUtils.closeQuietly(inputStream);
            CloseUtils.closeQuietly(outputStream);
        }
    }


    /**
     * TODO: Category class
     * @author Benjamin Touchard
     */

    public static final class Category extends Entity implements Entity.WithData {

        public static final String TAG						= DataHelper.TAG + ".Category";

        public static final String TABLE 					= "category";
        public static final Uri CONTENT_URI 				= Uri.parse("content://" + CategoryProvider.AUTHORITY + "/" + TABLE);
        public static final String CONTENT_TYPE 			= "vnd.android.cursor.dir/vnd.kolapsis.rtdb.category";
        public static final String MIME_TYPE 				= "image/jpeg"; //"vnd.android.cursor.item/vnd.kolapsis.rtdb.category";
        public static final int DIRECTORY 					= 100;
        public static final int ITEM 						= 101;

        public static class Columns extends BaseColumnsWithData {

            public static final String TITLE 				= "title";

            public static final String[] FULL_PROJECTION 	= new String[] {
                    ID,
                    ACCOUNT,
                    SOURCE_ID,
                    TITLE,
                    DATA,
                    SIZE,
                    DISPLAY_NAME,
                    DIRTY,
                    DELETED
            };

            public static final String CREATE_TABLE 		= "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + ID + " INTEGER PRIMARY KEY, "
                    + ACCOUNT + " TEXT NOT NULL, "
                    + SOURCE_ID + " INTEGER, "
                    + TITLE + " TEXT, "
                    + DATA + " TEXT, "
                    + SIZE + " INTEGER DEFAULT 0, "
                    + DISPLAY_NAME + " TEXT, "
                    + DIRTY + " INTEGER DEFAULT 0, "
                    + DELETED + " INTEGER DEFAULT 0"
                    + ");";

            public static final String DROP_TABLE 			= "DROP TABLE IF EXISTS " + TABLE + ";";
        }

        public static final class Loader extends CursorLoader {
            private final ForceLoadContentObserver mObserver = new ForceLoadContentObserver();
            private final Account mAccount;
            private final Bundle mArgs;
            public Loader(Context context, Account account, Bundle args) {
                super(context);
                if (sContext == null) setContext(context);
                mAccount = account;
                mArgs = args;
            }
            @Override
            public Cursor loadInBackground() {
                String where = BaseColumns.getWhere(mAccount);
                if (mArgs != null && !mArgs.isEmpty()) {
                    for (String key : mArgs.keySet())
                        where += " AND " + key + "='" + mArgs.getString(key) + "'";
                }
                where += " AND " + Columns.DELETED + "=0";
                // Log.v(TAG, where);
                Cursor cursor = sContentResolver.query(CONTENT_URI, null, where, null, null);
                if (cursor != null) {
                    cursor.getCount();
                    cursor.registerContentObserver(mObserver);
                }
                return cursor;
            }
        }

        public static List<Category> getMarkedAs(Account account, MarkedAs mark) {
            return select(account, mark.value());
        }

        public static long contains(Account account, Category entity) {
            if (entity == null) return 0;
            long id = 0;
            String where = BaseColumns.getWhere(account);
            where += " AND " + Columns.SOURCE_ID + " = " + entity.getSourceId();
            Cursor cursor = sContentResolver.query(CONTENT_URI, new String[] { Columns.ID }, where, null, null);
            if (cursor != null && cursor.moveToFirst()) id = cursor.getLong(0);
            CloseUtils.closeQuietly(cursor);
            return id;
        }

        public static boolean hasChange(Category local, Category remote) {
            if (local == null || remote == null) return false;
            if (local.title != null && remote.title != null && !local.title.equals(remote.title)) return true;
            return false;
        }

        public static long getIdFromSourceId(Account account, int sourceId) {
            if (sourceId == 0) return 0;
            long localId = 0;
            String where = Columns.ACCOUNT + " = '" + account.name + "' AND " + Columns.SOURCE_ID + " = " + sourceId;
            Cursor cursor = sContentResolver.query(CONTENT_URI, new String[] { Columns.ID }, where, null, null);
            if (cursor != null && cursor.moveToFirst()) localId = cursor.getLong(0);
            CloseUtils.closeQuietly(cursor);
            return localId;
        }
        public static int getSourceIdFromId(Account account, long localId) {
            int sourceId = 0;
            String where = Columns.ACCOUNT + " = '" + account.name + "' AND " + Columns.ID + " = " + localId;
            Cursor cursor = sContentResolver.query(CONTENT_URI, new String[] { Columns.SOURCE_ID }, where, null, null);
            if (cursor != null && cursor.moveToFirst()) sourceId = cursor.getInt(0);
            CloseUtils.closeQuietly(cursor);
            return sourceId;
        }

        public static Category select(long localId) {
            return select(ContentUris.withAppendedId(CONTENT_URI, localId));
        }
        public static Category select(Uri uri) {
            Category entity = null;
            Cursor cursor = sContentResolver.query(uri, null, null , null, null);
            if (cursor != null && cursor.moveToFirst()) entity = cursorToCategory(cursor);
            CloseUtils.closeQuietly(cursor);
            return entity;
        }
        public static Category select(Account account, int sourceId) {
            Category entity = null;
            String where = Columns.getWhere(account) + " AND " + Columns.SOURCE_ID + " = " + sourceId;
            Cursor cursor = sContentResolver.query(CONTENT_URI, null, where , null, null);
            if (cursor != null && cursor.moveToFirst()) entity = cursorToCategory(cursor);
            CloseUtils.closeQuietly(cursor);
            return entity;
        }
        public static List<Category> select(Account account) {
            return select(account, null);
        }
        public static List<Category> select(Account account, String where) {
            return select(account, where, null);
        }
        public static List<Category> select(Account account, String where, String order) {
            List<Category> list = new ArrayList<>();
            String w = BaseColumns.getWhere(account);
            if (!TextUtils.isEmpty(where)) w += " AND (" + where + ")";
            Cursor cursor = sContentResolver.query(CONTENT_URI, null, w , null, order);
            if (cursor != null && cursor.moveToFirst())
                do { list.add(cursorToCategory(cursor)); }
                while (cursor.moveToNext());
            CloseUtils.closeQuietly(cursor);
            return list;
        }

        public static Uri insert(Account account, Category entity) {
            return insert(account, entity, false);
        }
        public static Uri insert(Account account, Category entity, Uri fileUri) {
            return insert(account, entity, fileUri, false);
        }
        public static Uri insert(Account account, Category entity, boolean callerIsSyncAdapter) {
            return insert(account, entity, null, callerIsSyncAdapter);
        }
        public static Uri insert(Account account, Category entity, Uri fileUri, boolean callerIsSyncAdapter) {
            Uri uri = CONTENT_URI.buildUpon().appendQueryParameter(Constants.CALLER_IS_SYNCADAPTER, String.valueOf(callerIsSyncAdapter)).build();
            ContentValues values = getContentValues(account, entity, fileUri);
            Uri newUri = sContentResolver.insert(uri, values);
            // Log.i(TAG, "--> inserted " + newUri + ", fileUri: " + fileUri);
            if (newUri != null && fileUri != null) setContent(newUri, fileUri);
            return newUri;
        }

        public static int update(Account account, Category entity) {
            return update(account, entity, false);
        }
        public static int update(Account account, Category entity, Uri fileUri) {
            return update(account, entity, fileUri, false);
        }
        public static int update(Account account, Category entity, boolean callerIsSyncAdapter) {
            return update(account, entity, null, callerIsSyncAdapter);
        }
        public static int update(Account account, Category entity, Uri fileUri, boolean callerIsSyncAdapter) {
            Uri uri = entity.getUri().buildUpon().appendQueryParameter(Constants.CALLER_IS_SYNCADAPTER, String.valueOf(callerIsSyncAdapter)).build();
            ContentValues contentValue = getContentValues(account, entity, fileUri);
            int cnt = sContentResolver.update(uri, contentValue, null, null);
            // Log.i(TAG, "--> updated " + entity.getUri());
            if (cnt > 0 && fileUri != null) setContent(entity.getUri(), fileUri);
            return cnt;
        }

        public static int delete(Account account, long localId) {
            return delete(account, localId, false);
        }
        public static int delete(Account account, long localId, boolean callerIsSyncAdapter) {
            Uri uri = ContentUris.withAppendedId(CONTENT_URI, localId).buildUpon().appendQueryParameter(Constants.CALLER_IS_SYNCADAPTER, String.valueOf(callerIsSyncAdapter)).build();
            int count = sContentResolver.delete(uri, null, null);
            // Log.i(TAG, "--> deleted " + uri + " > " + count);
            return count;
        }

        private static ContentValues getContentValues(Account account, Category entity, Uri fileUri) {
            ContentValues values = categoryToContent(entity);
            if (entity.getId() == 0) values.put(Columns.ACCOUNT, account.name);
            if (fileUri != null) {
                values.put(Columns.DATA, sPath + fileUri.getLastPathSegment());
                values.put(Columns.SIZE, getUriFileSize(fileUri));
                values.put(Columns.DISPLAY_NAME, StringUtils.removeExtension(fileUri.getLastPathSegment()));
            }
            return values;
        }

        public static Category cursorToCategory(Cursor c){
            Category entity = new Category();
            entity.setId(c.getInt(c.getColumnIndex(Columns.ID)));
            entity.setSourceId(c.getInt(c.getColumnIndex(Columns.SOURCE_ID)));
            entity.setTitle(c.getString(c.getColumnIndex(Columns.TITLE)));
            entity.setData(c.getString(c.getColumnIndex(Columns.DATA)));
            entity.setSize(c.getLong(c.getColumnIndex(Columns.SIZE)));
            return entity;
        }

        private static ContentValues categoryToContent(Category entity){
            ContentValues values = new ContentValues();
            values.put(Columns.SOURCE_ID, entity.getSourceId());
            values.put(Columns.TITLE, entity.getTitle());
            return values;
        }

        public static Category from(Account account, JSONObject data){
            Category entity = new Category();
            entity.fromJSON(data);
            entity.setId(getIdFromSourceId(account, entity.getSourceId()));
            return entity;
        }

        public Category() {}

        private long id = 0;
        private int sourceId = 0;
        private String title, data;
        private long size;

        public String getApiPath() {
            return Category.TABLE;
        }
        public boolean hasData() {
            return false;
        }

        public void fromJSON(JSONObject json) {
            try {
                if (json.has("id")) setSourceId(json.getInt("id"));
                if (json.has(Columns.TITLE)) setTitle(json.getString(Columns.TITLE));
                if (json.has("data")) setData(json.getString("data"));
                if (json.has("size")) setSize(json.getLong("size"));
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.getMessage());
            }
        }

        public JSONObject asJSON() {
            JSONObject json = new JSONObject();
            try {
                json.put(Columns.TITLE, getTitle());
                json.put("data", StringUtils.getFilename(getData()));
                json.put("size", getSize());
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.getMessage());
                json = null;
            }
            return json;
        }

        public static Uri getUri(long uid) {
            if (uid == 0) return null;
            return ContentUris.withAppendedId(CONTENT_URI, uid);
        }

        public Uri getUri() {
            return Category.getUri(id);
        }

        public long getId() {
            return id;
        }
        public void setId(long id) {
            this.id = id;
        }

        public int getSourceId() {
            return sourceId;
        }
        public void setSourceId(int sourceId) {
            this.sourceId = sourceId;
        }

        public String getTitle() {
            return title;
        }
        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public String getData() {
            return data;
        }
        public void setData(String data) {
            this.data = data;
        }

        @Override
        public long getSize() {
            return size;
        }
        public void setSize(long size) {
            this.size = size;
        }

        @Override
        public String getFileName() {
            return data != null ? StringUtils.getFilename(data) : null;
        }

        @Override
        public File getLocalFile() {
            try {
                InputStream is = sContentResolver.openInputStream(getUri());
                File tmp = new File(sContext.getCacheDir(), StringUtils.getFilename(data));
                OutputStream os = new FileOutputStream(tmp);
                IOUtils.copy(is, os);
                os.close();
                is.close();
                return tmp;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Category)) return false;
            return sourceId == ((Category) o).sourceId;
        }

        @Override
        public String toString() {
            return "Category #" + id + ", sourceId: " + sourceId + ", title: " + title;
        }

    }
}
