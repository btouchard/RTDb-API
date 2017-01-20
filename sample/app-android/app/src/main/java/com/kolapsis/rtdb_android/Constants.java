package com.kolapsis.rtdb_android;

public interface Constants {

    boolean DEBUG                       = true;

    String APP_NAME 					= "RTDbApi";
    String APP_PACKAGE 					= "com.kolapsis.rtdb_android";
    String APP_VERSION 					= "1.0";
    String APP_NAMESPACE				= "http://schemas.android.com/apk/res/" + APP_PACKAGE;

    String AUTHORITY 					= APP_PACKAGE + ".providers";
    String ACCOUNT_TYPE 				= APP_PACKAGE + ".account";
    String AUTHTOKEN_TYPE 				= APP_PACKAGE + ".account.token";

    String BASE_URL					    = "http://192.168.0.28:3000";
    String API_BASE_URL					= BASE_URL + "/api";
    String API_TOKEN_NAME				= "X-APP-TOKEN";
    String SERVER_LAST_SYNC 			= "server_last_sync";

    int BDD_VERSION 					= 1;
    String BDD_NAME 					= "app.db";
    String CALLER_IS_SYNCADAPTER 		= "caller_is_syncadapter";

    int RESULT_REQUEST_PERMS			= 0;
    int RESULT_NEW_ACCOUNT				= 1;
}
