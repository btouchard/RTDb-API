package com.kolapsis.rtdb_android.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;

public class CategorySyncService extends Service {

	public static final String TAG 							= Constants.APP_NAME + ".CategorySyncService";
	private static final Object sSyncAdapterLock 			= new Object();
	private static CategorySyncAdapter sSyncAdapter 			= null;

	@Override
	public void onCreate() {
		Log.v(TAG, "onCreate()");
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new CategorySyncAdapter(getApplicationContext(), true);
			}
		}
	}

	@Override
	public void onDestroy() {
		Log.v(TAG, "onDestroy()");
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return sSyncAdapter.getSyncAdapterBinder();
	}

}
