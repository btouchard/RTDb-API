package com.kolapsis.rtdb_android.services;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import com.android.volley.*;
import com.android.volley.toolbox.*;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.helpers.AccountHelper;
import com.kolapsis.rtdb_android.helpers.Entity;
import com.kolapsis.rtdb_android.utils.CloseUtils;
import com.kolapsis.rtdb_android.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RTDbApi {

    public static final String TAG = Constants.APP_NAME + ".RTDbApi";

    private static Context context;
    private static AccountManager accountManager;
    private static RTDbApi instance;

    @SuppressLint("CommitPrefEdits")
    public static void setContext(Context context) {
        //Log.i(TAG, "--> setContext: " + context.getPackageName());
        RTDbApi.context = context;
        if (RTDbApi.context != null) {
            accountManager = AccountManager.get(RTDbApi.context);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(RTDbApi.context);
            // if (pref.getString("api_url", null) != null) pref.edit().remove("api_url").commit();
            if (pref.getString("api_url", null) == null) pref.edit().putString("api_url", Constants.API_BASE_URL).commit();
        }
    }

    public static RTDbApi getInstance() {
        if (instance == null) instance = new RTDbApi();
        return instance;
    }

    public static final class RequestException extends Exception {

        public static String getMessage(String msg) {
            if (StringUtils.isJSON(msg))
                return getMessage(StringUtils.toJSON(msg));
            return msg;
        }
        public static String getMessage(JSONObject json) {
            if (json.has("msg"))
                try { return json.getString("msg"); }
                catch (JSONException ignored) {}
            return null;
        }

        RequestException(String msg) {
            super(msg);
        }
    }

    private static abstract class RequestBase {
        protected static RequestQueue queue;
        public static RequestQueue getQueue() {
            if (queue == null) queue = Volley.newRequestQueue(context);
            return queue;
        }

        protected String token, url, tag = null;
        protected int method;
        protected boolean cache = false;
        protected NetworkResponse response;

        public RequestBase(Account account, @NonNull String url, int method) {
            if (account != null)
                this.token = accountManager.getUserData(account, AccountHelper.UserColumns.TOKEN);
            this.url = url;
            this.method = method;
        }

        @SuppressWarnings("unused")
        public RequestBase shouldCache(boolean cache) {
            this.cache = cache;
            return this;
        }

        public void method(int method) {
            this.method = method;
        }

        public RequestBase tag(@NonNull String tag) {
            this.tag = tag;
            return this;
        }

        public String header(@NonNull String name) {
            if (response == null || response.headers == null) return null;
            return response.headers.containsKey(name) ? response.headers.get(name) : null;
        }

        @SuppressWarnings("unused")
        protected String getMethod(int m) {
            switch (m) {
                case com.android.volley.Request.Method.GET:
                    return "GET";
                case com.android.volley.Request.Method.POST:
                    return "POST";
                case com.android.volley.Request.Method.PUT:
                    return "PUT";
                case com.android.volley.Request.Method.DELETE:
                    return "DELETE";
            }
            return null;
        }
    }

    public static class UploadRequest extends Request {
        protected Map<String, File> files = new HashMap<>();

        public UploadRequest(Account account, @NonNull String url) {
            super(account, url, com.android.volley.Request.Method.POST);
        }

        public UploadRequest file(@NonNull File file) {
            String name = StringUtils.removeExtension(StringUtils.getFilename(file.getName()));
            return file(name, file);
        }
        public UploadRequest file(@NonNull String name, @NonNull File file) {
            files.put(name, file);
            return this;
        }

        public JSONObject json() throws RequestException {
            RequestFuture<JSONObject> future = RequestFuture.newFuture();
            if (Constants.DEBUG) Log.v(TAG, "request [" + getMethod(method) + "] " + url + ", files: " + files);
            JsonObjectRequest request = new JsonObjectRequest(method, url, null, future, future) {

                private String twoHyphens = "--";
                private String lineEnd = "\r\n";
                private String boundary = "rtdbapi-" + System.currentTimeMillis();

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String>  headers = new HashMap<>();
                    if (!TextUtils.isEmpty(token))
                        headers.put(Constants.API_TOKEN_NAME, token);
                    return headers;
                }

                @Override
                public String getBodyContentType() {
                    return "multipart/form-data;boundary=" + boundary;
                }

                @Override
                public byte[] getBody() {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(bos);
                        for (String key : files.keySet()) {
                            File file = files.get(key);
                            dos.writeBytes(twoHyphens + boundary + lineEnd);
                            dos.writeBytes("Content-Disposition: form-data; name=\"" + key + "\"; filename=\"" + file.getName() + "\"" + lineEnd);
                            dos.writeBytes(lineEnd);
                            FileInputStream fis = new FileInputStream(file);
                            int bytesAvailable = fis.available();

                            int maxBufferSize = 1024 * 1024;
                            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
                            byte[] buffer = new byte[bufferSize];

                            int bytesRead = fis.read(buffer, 0, bufferSize);

                            while (bytesRead > 0) {
                                dos.write(buffer, 0, bufferSize);
                                bytesAvailable = fis.available();
                                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                                bytesRead = fis.read(buffer, 0, bufferSize);
                            }
                            dos.writeBytes(lineEnd);
                        }
                        dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                        return bos.toByteArray();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                    return null;
                }

                @Override
                protected Response<JSONObject> parseNetworkResponse(NetworkResponse res) {
                    response = res;
                    return super.parseNetworkResponse(res);
                }
                @Override
                protected VolleyError parseNetworkError(VolleyError volleyError) {
                    response = volleyError.networkResponse;
                    return super.parseNetworkError(volleyError);
                }
            };
            request.setShouldCache(cache);
            if (!TextUtils.isEmpty(tag)) request.setTag(tag);
            getQueue().add(request);
            return finalize(future);
        }
    }
    public static class Request extends RequestBase {
        protected Map<String, String> data = new HashMap<>();

        public Request(@NonNull String url, int method) {
            super(null, url, method);
        }

        public Request(Account account, @NonNull String url, int method) {
            super(account, url, method);
        }

        public Request(Account account, @NonNull String url) {
            super(account, url, com.android.volley.Request.Method.GET);
        }

        public Request(@NonNull String url, @NonNull String token) {
            super(null, url, com.android.volley.Request.Method.GET);
            this.token = token;
        }

        public Request data(@NonNull Map<String, String> data) {
            this.data.putAll(data);
            return this;
        }

        public Request data(@NonNull String name, String value) {
            data.put(name, value);
            return this;
        }

        public Request data(@NonNull JSONObject json) throws JSONException {
            for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                data.put(key, json.getString(key));
            }
            return this;
        }

        protected JSONObject finalize(RequestFuture<JSONObject> future) throws RequestException {
            try {
                return future.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                // Log.e(TAG, e.getMessage(), e);
                String res = response != null ? new String(response.data) : null;
                if (res == null) res = e.getMessage();
                throw new RequestException(RequestException.getMessage(res));
            }
        }

        public JSONObject json() throws RequestException {
            RequestFuture<JSONObject> future = RequestFuture.newFuture();
            if (Constants.DEBUG) Log.v(TAG, "request [" + getMethod(method) + "] " + url + ", data: " + data);
            JSONObject json = null;
            if (data != null && data.size() > 0) json = new JSONObject(data);
            JsonObjectRequest request = new JsonObjectRequest(method, url, json, future, future) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String>  headers = new HashMap<>();
                    if (!TextUtils.isEmpty(token))
                        headers.put(Constants.API_TOKEN_NAME, token);
                    return headers;
                }
                @Override
                protected Response<JSONObject> parseNetworkResponse(NetworkResponse res) {
                    response = res;
                    return super.parseNetworkResponse(res);
                }
                @Override
                protected VolleyError parseNetworkError(VolleyError volleyError) {
                    response = volleyError.networkResponse;
                    return super.parseNetworkError(volleyError);
                }
            };
            request.setShouldCache(cache);
            if (!TextUtils.isEmpty(tag)) request.setTag(tag);
            getQueue().add(request);
            return finalize(future);
        }

        public byte[] bytes() throws RequestException {
            RequestFuture<byte[]> future = RequestFuture.newFuture();
            // if (Constants.DEBUG) Log.v(TAG, "request [" + getMethod(method) + "] " + url + ", data: " + data);
            InputStreamRequest request = new InputStreamRequest(method, url, future, future) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String>  headers = new HashMap<>();
                    if (!TextUtils.isEmpty(token))
                        headers.put(Constants.API_TOKEN_NAME, token);
                    return headers;
                }
                @Override
                protected Response<byte[]> parseNetworkResponse(NetworkResponse res) {
                    response = res;
                    return super.parseNetworkResponse(res);
                }
                @Override
                protected VolleyError parseNetworkError(VolleyError volleyError) {
                    response = volleyError.networkResponse;
                    return super.parseNetworkError(volleyError);
                }
            };
            request.setShouldCache(cache);
            if (!TextUtils.isEmpty(tag)) request.setTag(tag);
            getQueue().add(request);
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RequestException(e.getMessage());
            }
        }

        public Request head() throws RequestException {
            cache = false;
            method = com.android.volley.Request.Method.HEAD;
            RequestFuture<String> future = RequestFuture.newFuture();
            StringRequest request = new StringRequest(method, url, future, future) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String>  headers = new HashMap<>();
                    if (!TextUtils.isEmpty(token))
                        headers.put(Constants.API_TOKEN_NAME, token);
                    return headers;
                }
                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse res) {
                    response = res;
                    return super.parseNetworkResponse(res);
                }
                @Override
                protected VolleyError parseNetworkError(VolleyError volleyError) {
                    response = volleyError.networkResponse;
                    return super.parseNetworkError(volleyError);
                }
            };
            request.setShouldCache(cache);
            if (!TextUtils.isEmpty(tag)) request.setTag(tag);
            getQueue().add(request);
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RequestException(e.getMessage());
            }
            return this;
        }

        // ACCESSEURS PLUS RAPIDES

        public boolean success() throws RequestException {
            try {
                JSONObject json = json();
                return json.has("success") && json.getBoolean("success");
            } catch (JSONException e) {
                throw new RequestException(e.getMessage());
            }
        }

        public JSONObject result() throws RequestException {
            try {
                JSONObject json = json();
                if (json.has("success") && !json.getBoolean("success")) throw new RequestException(RequestException.getMessage(json));
                return json.getJSONObject("result");
            } catch (JSONException e) {
                throw new RequestException(e.getMessage());
            }
        }

        public JSONArray array() throws RequestException {
            try {
                JSONObject json = json();
                if (json.has("success") && !json.getBoolean("success")) throw new RequestException(RequestException.getMessage(json));
                return json.getJSONArray("result");
            } catch (JSONException e) {
                throw new RequestException(e.getMessage());
            }
        }
    }

    private String apiUrl = Constants.API_BASE_URL;

    private RTDbApi() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref != null) setApiUrl(pref.getString("api_url", Constants.API_BASE_URL));
    }

    public void setApiUrl(String url) {
        apiUrl = url;
    }
    public String getApiUrl() {
        return apiUrl;
    }

	/*
	 * TODO : AUTHENTIFICATION
	 */

    /**
     * Validation d'un token d'authentification
     * @param token Auth token to verify
     * @return Successful state
     * @throws AuthenticatorException Authentication error
     * @throws IOException Internet call error
     */

    public boolean isValidToken(String token) throws IOException {
        if (TextUtils.isEmpty(token)) return false;
        boolean success;
        try {
            String url = apiUrl + "/verify";
            success = new Request(url, token).success();
            //if (Constants.DEBUG) Log.d(TAG+".Auth", "--> token: '" + token + "', valide: '" + success + "'");
        } catch (RequestException e) {
            Log.e(TAG+".Auth", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        }
        return success;
    }

    /**
     * Authentification via Account
     * @param account User system account
     * @return User token
     * @throws AuthenticatorException Authentication error
     */
    public String authenticate(Account account) throws AuthenticatorException, IOException {
        if (account == null) return null;
        String email = AccountHelper.getEmail();
        String password = AccountHelper.getPassword();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) return null;
        return authenticate(email, password);
    }

    /**
     * Authentification via Email / Password
     * @param email User email
     * @param password User password
     * @return User token
     * @throws AuthenticatorException Authentication error
     * @throws IOException Internet call error
     */
    public String authenticate(String email, String password) throws AuthenticatorException, IOException {
        String url = apiUrl + "/authenticate";
        Log.d(TAG+".Auth", "--> authenticate('" + url + "', '" + email + "', '" + password + "')");
        String token;
        try {
            JSONObject json = new Request(url, com.android.volley.Request.Method.POST)
                    .data("email", email).data("password", password).result();
            Log.v(TAG+".Auth", "-> json:" + json);
            token = json.getString("token");
            Log.d(TAG+".Auth", "--> token: '" + token + "'");
        } catch (RequestException e) {
            Log.e(TAG+".Auth", "RequestException: " + e.getMessage());
            throw new AuthenticatorException(e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG+".Auth", "JSONException: " + e.getMessage());
            throw new IOException(e.getMessage());
        }
        return token;
    }

    /**
     * Chargement des données d'un compte authentifié
     * @param token User token
     * @return Les données de l'utilisateur
     */
    public Bundle loadUser(String token) throws AuthenticatorException, IOException {
        Bundle data = new Bundle();
        try {
            String url = apiUrl + "/verify";
            //Log.d(TAG+".Auth", "---> loadUser('" + url + "', '" + token + "')");
            JSONObject json = new Request(url, token).result();
            data.putString(AccountHelper.UserColumns.ID, String.valueOf(json.getInt(AccountHelper.UserColumns.ID)));
            data.putString(AccountHelper.UserColumns.FIRSTNAME, json.getString(AccountHelper.UserColumns.FIRSTNAME));
            data.putString(AccountHelper.UserColumns.LASTNAME, json.getString(AccountHelper.UserColumns.LASTNAME));
        } catch (RequestException e) {
            Log.e(TAG+".Auth", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG+".Auth", "JSONException: " + e.getMessage());
            throw new AuthenticatorException(e.getMessage());
        }
        return data;
    }

	/*
	 * TODO : DATA LOADER, SAVER, DELETER
	 */

    /**
     * Chargement des Entités
     * @param clzz Class de l'entité a utiliser.
     * @param account Compte a synchroniser
     * @return Une <b>liste</b> d'entité <b>&lt;Entity&gt</b>
     * @throws AuthenticatorException Authentication error
     * @throws IOException Internet call error
     */
    public <T extends Entity> List<T> load(Class<T> clzz, Account account) throws AuthenticatorException, IOException {
        return load(clzz, account, null);
    }
    public <T extends Entity> List<T> load(Class<T> clzz, Account account, Map<String, String> args) throws AuthenticatorException, IOException {
        List<T> list = new ArrayList<>();
        try {
            T inst = clzz.newInstance();
            String url = apiUrl + "/" + inst.getApiPath();
            Log.d(TAG+".Load", "--> load('" + url + "')");
            Request rq = new Request(account, url);
            if (args != null && !args.isEmpty())
                for (String key : args.keySet())
                    rq.data(key, args.get(key));
            JSONArray ints = rq.array();
            Log.d(TAG+".Load", "--> result: " + ints);
            for (int i=0; i<ints.length(); i++) {
                JSONObject inter = ints.getJSONObject(i);
                inst = clzz.newInstance();
                inst.fromJSON(inter);
                if (inst.getSourceId() > 0) list.add(inst);
            }
        } catch (RequestException e) {
            Log.e(TAG+".Load", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG+".Load", "JSONException: " + e.getMessage());
            throw new AuthenticatorException(e.getMessage());
        } catch (InstantiationException e) {
            Log.e(TAG+".Load", "InstantiationException: " + e.getMessage());
        } catch (IllegalAccessException e) {
            Log.e(TAG+".Load", "IllegalAccessException: " + e.getMessage());
        }
        return list;
    }

    /**
     * Chargement d'une Entité
     * @param clzz Class de l'entité à charger.
     * @param account Compte a synchroniser
     * @return Une entité <b>&lt;Entity&gt</b>
     * @throws AuthenticatorException Authentication error
     * @throws IOException Internet call error
     */
    public <T extends Entity> T load(Class<T> clzz, Account account, int id) throws AuthenticatorException, IOException {
        Map<String, String> args = new HashMap<>();
        args.put("id", String.valueOf(id));
        List<T> list = load(clzz, account, args);
        return list.size() > 0 ? list.get(0) : null;
    }

    /**
     * Ajout d'une entité
     * @param account Compte a synchroniser
     * @param entity Entité a sauvegarder
     * @return Un <b>entier</b> positif correspondant au nouvel identifiant distant, ou 0 si une erreur est survenue
     * @throws AuthenticatorException Authentication error
     * @throws IOException Internet call error
     */
    public <T extends Entity> int save(Account account, T entity) throws AuthenticatorException, IOException {
        int sourceId = 0;
        try {
            String url = apiUrl + "/" + entity.getApiPath();
            if (entity.getSourceId() > 0) url += "/" + entity.getSourceId();
            Log.d(TAG+".Save", "---> " + (entity.getSourceId() == 0 ? "insert" : "update") + "('" + url + "')");
            Log.d(TAG+".Save", "---> data: " + entity.asJSON());
            Request rq = new Request(account, url).data(entity.asJSON());
            if (entity.getSourceId() > 0) rq.method(com.android.volley.Request.Method.PUT);
            else rq.method(com.android.volley.Request.Method.POST);
            JSONObject result = rq.result();
            Log.d(TAG+".Save", "---> result: " + result);
            if (result.has("id")) sourceId = result.getInt("id");
        } catch (RequestException e) {
            Log.e(TAG+".Save", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG+".Save", "JSONException: " + e.getMessage());
            throw new AuthenticatorException(e.getMessage());
        }
        return sourceId;
    }

    /**
     * Suppression d'une entité
     * @param account Compte a synchroniser
     * @param entity Identifiant distant de l'entité à supprimer
     * @return Un <b>booléen</b>, <b>true</b> si la suppression est effectuée avec succés, sinon <b>false</b>
     * @throws AuthenticatorException Authentication error
     * @throws IOException Internet call error
     */
    public <T extends Entity> boolean delete(Account account, T entity) throws AuthenticatorException, IOException {
        boolean success;
        try {
            String url = apiUrl + "/" + entity.getApiPath() + "/" + entity.getSourceId();
            Log.d(TAG+".Delete", "---> delete('" + url + "')");
            //Log.d(TAG+".Delete", "---> result: " + request.asString());
            success = new Request(account, url, com.android.volley.Request.Method.DELETE).success();
        } catch (RequestException e) {
            Log.e(TAG+".Delete", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        }
        return success;
    }

    public <T extends Entity.WithData> long fileSize(Account account, T entity) throws AuthenticatorException, IOException {
        String url = apiUrl + "/medias/" + entity.getApiPath() + "/" + entity.getData();
        //Log.d(TAG+".FileSize", "---> fileSize('" + url + "')");
        //Log.d(TAG+".FileSize", "---> size: " + size);
        long size = 0;
        try {
            Request rq = new Request(account, url).head();
            if (rq.header("Content-Length") != null) {
                String length = rq.header("Content-Length");
                if (StringUtils.isLong(length)) size = Long.parseLong(length);
            }
        } catch (RequestException e) {
            Log.e(TAG+".FileSize", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        }
        return size;
    }

    public <T extends Entity.WithData> Uri download(Account account, T entity) throws AuthenticatorException, IOException {
        String url = apiUrl + "/medias/" + entity.getApiPath() + "/" + entity.getData();
        Log.d(TAG+".Download", "---> download('" + url + "')");
        return download(account, url);
    }
    public Uri download(Account account, String url) throws AuthenticatorException, IOException {
        Uri uri = null;
        try {
            byte[] bytes = new Request(account, url).bytes();
            InputStream is = new ByteArrayInputStream(bytes);
            try {
                String name = StringUtils.getFilename(url);
                File tmp = new File(context.getCacheDir(), name);
                FileOutputStream os = new FileOutputStream(tmp);
                try {
                    final byte[] buffer = new byte[1024];
                    int read;
                    while ((read = is.read(buffer)) != -1)
                        os.write(buffer, 0, read);
                    os.flush();
                } finally {
                    CloseUtils.closeQuietly(os);
                }
                uri = Uri.fromFile(tmp);
                Log.d(TAG+".Download", "---> tmp uri: " + uri + ", size: " + tmp.length());
            } catch (Exception e) {
                Log.e(TAG+".Download", "Exception: " + e.getMessage());
            } finally {
                CloseUtils.closeQuietly(is);
            }
        } catch (RequestException e) {
            Log.e(TAG+".Download", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        }
        return uri;
    }

    public <T extends Entity.WithData> boolean upload(Account account, T entity) throws AuthenticatorException, IOException {
        boolean success;
        File file = new File(entity.getData());
        String url = apiUrl + "/medias/" + entity.getApiPath() + "/" + entity.getFileName();
        Log.d(TAG+".Upload", "---> upload('" + url + "')");
        try {
            success = new UploadRequest(account, url).file(file).success();
        } catch (RequestException e) {
            Log.e(TAG+".Upload", "RequestException: " + e.getMessage());
            throw new IOException(e.getMessage());
        }
        return success;
    }
}
