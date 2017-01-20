package com.kolapsis.rtdb_android.account;

import android.accounts.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.helpers.AccountHelper;
import com.kolapsis.rtdb_android.services.RTDbApi;

import java.io.IOException;

class Authenticator extends AbstractAccountAuthenticator {

	public static final String TAG 							= Constants.APP_NAME + ".Authenticator";
	
	public static final String PARAM_CONFIRM_CREDENTIALS 	= "confirmCredentials";
	public static final String PARAM_ACCOUNT_NAME 			= "email";
	public static final String PARAM_AUTHTOKEN_TYPE 		= "authtokenType";

	private final Context mContext;

	public Authenticator(Context context) {
		super(context);
		//Log.i(TAG, "onCreate();");
		mContext = context;
	}
	
	@Override
	public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) {
		//Log.v(TAG, "addAccount("+accountType+")");
		Intent intent = getAuthIntent(accountType);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}
	
	/*@Override
	public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) throws NetworkErrorException {
		Bundle result = super.getAccountRemovalAllowed(response, account);
		if (result != null && result.containsKey(AccountManager.KEY_BOOLEAN_RESULT) && !result.containsKey(AccountManager.KEY_INTENT)) {
	        final boolean removalAllowed = result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT);
	        if (removalAllowed) AccountHelper.empty(account, mContext);
	    }
		return result;
	}*/

	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
		Log.v(TAG, "confirmCredentials()");
		return null;
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
		Log.v(TAG, "editProperties()");
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle loginOptions) throws NetworkErrorException {
		Log.d(TAG, "-> getAuthToken("+authTokenType+")");

		if (!isValidToken(authTokenType)) {
			final Bundle result = new Bundle();
			result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
			return result;
		}
		final String pwd = AccountHelper.getPassword(account);
		Intent intent;
		if (pwd != null) {
			String authToken = null;
			try {
				authToken = RTDbApi.getInstance().authenticate(account);
			} catch (AuthenticatorException | IOException ignored) {}
			Log.i(TAG, "--> authToken: " + authToken);
			
			if (authToken != null) {
				final Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE, getAccountType(authTokenType));
				result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
				return result;
			} else {
				intent = getAuthIntent(authTokenType);
				intent.putExtra(PARAM_ACCOUNT_NAME, account.name);
				intent.putExtra(PARAM_AUTHTOKEN_TYPE, authTokenType);
				intent.putExtra(PARAM_CONFIRM_CREDENTIALS, true);
			}
		} else {
			intent = getAuthIntent(authTokenType);
			intent.putExtra(PARAM_ACCOUNT_NAME, account.name);
			intent.putExtra(PARAM_AUTHTOKEN_TYPE, authTokenType);
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
		}
		
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}

	@Override
	public String getAuthTokenLabel(String authTokenType) {
		// null means we don't support multiple authToken types
		Log.v(TAG, "getAuthTokenLabel()");
		return null;
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) {
		// This call is used to query whether the Authenticator supports
		// specific features. We don't expect to get called, so we always
		// return false (no) for any queries.
		Log.v(TAG, "hasFeatures()");
		final Bundle result = new Bundle();
		result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
		return result;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle loginOptions) {
		Log.v(TAG, "updateCredentials()");
		return null;
	}
	private boolean isValidToken(String authTokenType) {
		return authTokenType.equals(Constants.AUTHTOKEN_TYPE);
	}
	private String getAccountType(String authTokenType) {
		String type = null;
		if (authTokenType.equals(Constants.AUTHTOKEN_TYPE)) type = Constants.ACCOUNT_TYPE; 
		return type;
	}
	private Intent getAuthIntent(String type) {
		//if (type.equals(Constants.ACCOUNT_TYPE) || type.equals(Constants.AUTHTOKEN_TYPE)) return new Intent(mContext, AccountActivity.class); 
		//return null;
		return new Intent(mContext, LoginActivity.class);
	}
}
