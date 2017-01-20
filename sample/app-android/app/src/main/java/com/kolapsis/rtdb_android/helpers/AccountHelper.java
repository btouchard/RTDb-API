package com.kolapsis.rtdb_android.helpers;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.services.RTDbApi;

import java.io.IOException;

public class AccountHelper {
	
	public static final String TAG = Constants.APP_NAME + ".AccountHelper";
	
	public static final int SYNC_FINISH = 0;
	public static final int SYNC_PROGRESS = 1;

	public interface UserColumns {
		String TOKEN				= "token";
		String ID					= "id";
		String FIRSTNAME 			= "firstname";
		String LASTNAME 			= "lastname";
		String EMAIL				= "email";
		String PASSWORD				= "password";
		String UPDATE_DELAY			= "update_delay";
	}
	
	public interface ISyncCallback {
		void onSyncStateChange(boolean syncInProgress);
	}
	public static ISyncCallback SyncCallback;
	
	private static Context sContext;
	private static AccountManager sManager;
	private static Account sAccount;
	
	private AccountHelper() {}
	
	public static void setContext(Context context) {
		Log.i(TAG, "-> setContext: " + context.getPackageName());
		sContext = context;
		RTDbApi.setContext(sContext);
		DataHelper.setContext(sContext);

		sManager = AccountManager.get(sContext);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(sContext);
		if (sAccount == null && prefs.contains("selected_account")) {
			String name = prefs.getString("selected_account", null);
			if (name != null) sAccount = getAccountByTypeAndName(Constants.ACCOUNT_TYPE, name);
		}
	}
	
	public static String blockingGetAuthToken(Account account, String type) throws AuthenticatorException, IOException {
		//if (Constants.DEBUG) Log.d(TAG, "blockingGetAuthToken('"+AccountHelper.getAccountId(account)+"', '"+type+"')");
		String authToken = null;
		try {
			authToken = sManager.blockingGetAuthToken(account, type, false);
			if (Constants.AUTHTOKEN_TYPE.equals(type)) {
				boolean valid = RTDbApi.getInstance().isValidToken(authToken);
				//if (Constants.DEBUG) Log.d(TAG, "-> isValidToken: " + valid);
				if (!valid) {
					if (authToken != null) sManager.invalidateAuthToken(type, authToken);
					// authToken = sManager.blockingGetAuthToken(account, type, true);
					authToken = RTDbApi.getInstance().authenticate(account);
					sManager.setAuthToken(account, type, authToken);
				}
			}
		} catch (OperationCanceledException e) {
			Log.e(TAG, "OperationCanceledException" + e.getMessage());
		} finally {
			sManager.setUserData(account, UserColumns.TOKEN, authToken);
		}
		//if (Constants.DEBUG) Log.d(TAG, "-> token: " + authToken);
		return authToken;
	}
	
	public static Account[] getAccountsByType(String type) {
		return sManager.getAccountsByType(type);
	}
	public static Account getAccountByType(String type) {
		if (Constants.ACCOUNT_TYPE.equals(type)) return sAccount;
		return null;
	}
	public static Account getAccountByTypeAndName(String type, String name) {
		Account[] accounts = sManager.getAccountsByType(type);
		for (Account account : accounts) {
			if (name.equals(account.name)) return account;
		}
		return null;
	}
	
	public static boolean isEmpty() {
		return !hasAccount();
	}
	
	public static boolean isAccount(Account account) {
		if (Constants.ACCOUNT_TYPE.equals(account.type)) {
			if (!hasAccount()) return false;
			return getAccount().name.equals(account.name);
		}
		return false;
	}
	public static void setAccount(Account account) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(sContext);
		boolean change = false;
		if (Constants.ACCOUNT_TYPE.equals(account.type) && !account.equals(sAccount)) {
			sAccount = account;
			prefs.edit().putString("selected_account", sAccount.name).commit();
			change = true;
		}
		Log.v(TAG, "setCurrentAccount: " + change + " > " + account);
		//if (change) dispatchEvent(new Event(ACCOUNT_CHANGE, account));
	}
	
	public static String getData(Account account, String name) {
		if (account == null) return null;
		String value = sManager.getUserData(account, name);
		//Log.v(TAG, "getBoxData: " + name + " = " + value);
		return value;
	}
	
	public static boolean hasAccount() {
		if (sManager == null) return false;
		return sManager.getAccountsByType(Constants.ACCOUNT_TYPE).length > 0;
	}
	public static Account getAccount() {
		if (sAccount == null && hasAccount()) {
			Account[] accounts = sManager.getAccountsByType(Constants.ACCOUNT_TYPE);
			sAccount = accounts[0];
		}
		return sAccount;
	}
	
	public static String getEmail() {
		if (hasAccount()) return getEmail(getAccount());
		return null;
	}
	public static String getEmail(Account account) {
		if (sManager != null && account != null) {
			return sManager.getUserData(account, UserColumns.EMAIL);
		}
		return null;
	}

	public static String getPassword() {
		if (hasAccount()) return getPassword(getAccount());
		return null;
	}
	public static String getPassword(Account account) {
		if (sManager != null && account != null) {
			return sManager.getPassword(account);
		}
		return null;
	}
	
	public static String getData(String name) {
		return getData(getAccount(), name);
	}
	
	public static String getName() {
		return getData(UserColumns.FIRSTNAME);
	}

	public static int getUserId() {
		return getUserId(getAccount());
	}
	public static int getUserId(Account account) {
		String id = getData(account, UserColumns.ID);
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}