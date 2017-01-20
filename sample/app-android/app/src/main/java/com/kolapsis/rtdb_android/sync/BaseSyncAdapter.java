package com.kolapsis.rtdb_android.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.Context;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.helpers.AccountHelper;

public abstract class BaseSyncAdapter extends AbstractThreadedSyncAdapter {

	public static final String TAG = Constants.APP_NAME + ".BaseSyncAdapter";

	public BaseSyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	protected void onSyncStart(Account account, String authority) {
		if (Constants.DEBUG) Log.v(TAG, "onSyncStart: " + account.name + " " + authority);
		if (AccountHelper.SyncCallback != null) AccountHelper.SyncCallback.onSyncStateChange(true);
	}

	protected void onSyncFinish(Account account, String authority) {
		if (Constants.DEBUG) Log.v(TAG, "onSyncPerformed: " + account.name + " " + authority);
		if (AccountHelper.SyncCallback != null) AccountHelper.SyncCallback.onSyncStateChange(false);
	}

}
