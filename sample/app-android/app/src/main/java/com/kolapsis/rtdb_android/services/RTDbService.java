package com.kolapsis.rtdb_android.services;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.helpers.AccountHelper;
import com.kolapsis.rtdb_android.helpers.DataHelper;
import com.kolapsis.rtdb_android.helpers.Entity;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RTDbService extends Service implements IRTDbService {

    public static final String TAG = Constants.APP_NAME + ".RTDbService";

    public interface Listener {
        void onInsert(JSONObject result);
        void onUpdate(JSONObject result);
        void onDelete(JSONObject result);
    }

    private HashMap<String, List<Listener>> listeners = new HashMap<>();
    private RTDbServiceBinder binder;
    private AuthTask authTask;
    private Socket socket;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        AccountHelper.setContext(this);
        super.onCreate();
        binder = new RTDbServiceBinder(this);
        authTask = new AuthTask();
        authTask.execute();
    }

    @Override
    public void addListener(String table, Listener listener) {
        if (listeners.get(table) == null)
            listeners.put(table, new ArrayList<Listener>());
        listeners.get(table).add(listener);
    }

    @Override
    public void removeListener(String table, Listener listener) {
        if (listeners.get(table) != null)
            listeners.get(table).remove(listener);
    }

    public class AuthTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                String authToken = AccountHelper.blockingGetAuthToken(AccountHelper.getAccount(), Constants.AUTHTOKEN_TYPE);
                // Log.v(TAG, "authToken: " + authToken);
                if (authToken != null) {
                    IO.Options options = new IO.Options();
                    options.forceNew = true;
                    options.reconnection = true;
                    options.query = "token=" + authToken;
                    socket = IO.socket(Constants.BASE_URL, options);
                    socket.on(Socket.EVENT_CONNECT, onConnect);
                    // socket.on(Socket.EVENT_RECONNECT, onConnect);
                    socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
                    socket.on("insert", onInsert);
                    socket.on("update", onUpdate);
                    socket.on("delete", onDelete);
                    socket.connect();
                }
            } catch (URISyntaxException | AuthenticatorException | IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return binder;
    }

    @Override
    public void onDestroy() {
        if (authTask != null) authTask.cancel(true);
        if (socket != null) socket.disconnect();
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    private void dispatchEvent(String type, JSONObject result) {
        try {
            String table = result.getString("table");
            List<Listener> lts = listeners.get(table);
            if (lts == null) return;
            for (Listener l : lts) {
                if ("insert".equals(type)) l.onInsert(result);
                else if ("update".equals(type)) l.onUpdate(result);
                else if ("delete".equals(type)) l.onDelete(result);
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.v(TAG, "IO.Connected");
        }
    };
    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.v(TAG, "IO.Disconnected");
        }
    };
    private Emitter.Listener onInsert = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.v(TAG, "IO.Insert");
            try {
                Account account = AccountHelper.getAccount();
                JSONObject result = (JSONObject) args[0];
                if (result == null) return;
                Log.v(TAG, result.toString());
                dispatchEvent("insert", result);
                Entity entity = getEntity(account, result);
                Log.v(TAG, "-> entity: " + entity);
                if (entity != null && entity.getId() == 0) {
                    if (entity instanceof DataHelper.Category)
                        Entity.performLocalInsert(entity.getClass(), account, entity);
                }
            } catch (AuthenticatorException | IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    };
    private Emitter.Listener onUpdate = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            try {
                Log.v(TAG, "IO.Update");
                Account account = AccountHelper.getAccount();
                JSONObject result = (JSONObject)args[0];
                if (result == null) return;
                Log.v(TAG, result.toString());
                dispatchEvent("update", result);
                Entity entity = getEntity(account, result);
                Log.v(TAG, "-> entity: " + entity);
                if (entity != null && entity.getId() > 0) {
                    if (entity instanceof DataHelper.Category)
                        Entity.performLocalUpdate(entity.getClass(), account, entity);
                }
            } catch (AuthenticatorException | IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    };
    private Emitter.Listener onDelete = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.v(TAG, "IO.Delete");
            Account account = AccountHelper.getAccount();
            JSONObject result = (JSONObject)args[0];
            if (result == null) return;
            Log.v(TAG, result.toString());
            dispatchEvent("delete", result);
            Entity entity = getBySourceId(account, result);
            Log.v(TAG, "-> entity: " + entity);
            if (entity != null) {
                if (entity instanceof DataHelper.Category) {
                    DataHelper.Category.delete(account, entity.getId(), true);
                }
            }
        }
    };

    private Entity getBySourceId(Account account, JSONObject result) {
        try {
            String table = result.getString("table");
            JSONObject data = result.getJSONObject("row");
            if (DataHelper.Category.TABLE.equals(table))
                return DataHelper.Category.select(account, data.getInt("id"));
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }
    private Entity getEntity(Account account, JSONObject result) {
        try {
            String table = result.getString("table");
            JSONObject data = result.getJSONObject("row");
            if (DataHelper.Category.TABLE.equals(table))
                return DataHelper.Category.from(account, data);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }
}
