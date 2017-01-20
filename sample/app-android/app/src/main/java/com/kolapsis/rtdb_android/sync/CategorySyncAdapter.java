package com.kolapsis.rtdb_android.sync;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.content.ContentProviderClient;
import android.content.SyncResult;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.helpers.AccountHelper;
import com.kolapsis.rtdb_android.helpers.DataHelper;
import com.kolapsis.rtdb_android.helpers.Entity;

import java.io.IOException;

public class CategorySyncAdapter extends BaseSyncAdapter {

    public static final String TAG 							= Constants.APP_NAME + ".CategorySyncAdapter";

    public CategorySyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        if (extras.containsKey("upload") && extras.getBoolean("upload", false)) return;
        super.onSyncStart(account, authority);
        AccountHelper.setContext(getContext());

        boolean uploadOnly = extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false);
        boolean manualSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
        boolean initialize = extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE, false);
        boolean fullSync = !uploadOnly || manualSync || initialize;
        SharedPreferences syncMeta = getContext().getSharedPreferences("sync:" + authority + ":" + account.name, 0);
        long lastSync = syncMeta.getLong(Constants.SERVER_LAST_SYNC, 0);
        ContentResolver cr = getContext().getContentResolver();

        try {
            String authToken = AccountHelper.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE);
            if (!TextUtils.isEmpty(authToken)) {
                Entity.performSync(DataHelper.Category.class, account, fullSync, lastSync, cr, syncResult);
            }
        } catch (AuthenticatorException e) {
            Log.e(TAG, "AuthenticatorException", e);
            syncResult.stats.numAuthExceptions++;
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            syncResult.stats.numIoExceptions++;
        } finally {
            syncMeta.edit().putLong(Constants.SERVER_LAST_SYNC, System.currentTimeMillis()).commit();
            super.onSyncFinish(account, authority);
        }
    }
}
