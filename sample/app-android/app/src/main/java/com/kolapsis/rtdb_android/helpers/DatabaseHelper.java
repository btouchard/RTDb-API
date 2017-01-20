package com.kolapsis.rtdb_android.helpers;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String TAG 				= Constants.APP_NAME + ".DatabaseHelper";

    public DatabaseHelper(Context context) {
        super(context, Constants.BDD_NAME, null, Constants.BDD_VERSION);
    }

	public static void invalidate(Context context) {
		DatabaseHelper sql = new DatabaseHelper(context);
		sql.onUpgrade(sql.getWritableDatabase(), Constants.BDD_VERSION, Constants.BDD_VERSION);
	}

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.i(TAG, "Created db: " + Constants.BDD_NAME);
            db.execSQL(DataHelper.Category.Columns.CREATE_TABLE);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        }
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Log.i(TAG, "Upgrade db from: " + oldVersion + " to: " + newVersion);
            db.execSQL(DataHelper.Category.Columns.DROP_TABLE);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        }
        onCreate(db);
    }
}
