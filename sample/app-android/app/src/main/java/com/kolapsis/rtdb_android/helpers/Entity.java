package com.kolapsis.rtdb_android.helpers;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.net.Uri;
import android.util.Log;
import com.kolapsis.rtdb_android.Constants;
import com.kolapsis.rtdb_android.services.RTDbApi;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("unchecked")
public abstract class Entity {
	
	public static final String TAG 			= Constants.APP_NAME + ".Entity";

	public static class BaseColumns {

		public static final String ID		 		= "_id";
		public static final String SOURCE_ID 		= "sourceid";
		public static final String ACCOUNT 		= "account_name";
		public static final String DIRTY 			= "dirty";
		public static final String DELETED 		= "deleted";

		public static String getWhere(Account account) {
			if (account == null) return null;
			return ACCOUNT + "='" + account.name + "'";
		}
		
	}

	public static class BaseColumnsWithData extends BaseColumns {

		public static final String DATA 			= "_data";
		public static final String SIZE 			= "_size";
		public static final String DISPLAY_NAME		= "_display_name";

	}
	
	public interface WithData {
		int getSourceId();
		String getApiPath();
		String getData();
		long getSize();
		String getFileName();
		File getLocalFile();
	}
	
	public enum MarkedAs {
		INSERT(BaseColumns.SOURCE_ID + " = 0"),
		UPDATE(BaseColumns.DIRTY + " = 1"),
		DELETED(BaseColumns.DELETED + " = 1");
		private final String value;
		MarkedAs(String value){ this.value = value; }
		public String value(){ return value; }
	}

	public static long getUriFileSize(Uri uri) {
		try {
			File f = new File(uri.getPath());
			return f.length();
		} catch (Exception ignored) {}
		return 0;
	}

	public abstract Uri getUri();
	public abstract long getId();
	public abstract void setId(long id);
	public abstract void setSourceId(int id);
	public abstract int getSourceId();
	public abstract String getApiPath();

	public abstract void fromJSON(JSONObject json);
	public abstract JSONObject asJSON();

	/**
	 * Exécute une synchronisation de données (Entity) Client <-> Serveur
	 * @param clzz Class de l'entité a utiliser.
	 * @param account Compte a synchroniser
	 * @param fullSync Synchronisation complète
	 * @param cr Source de données (ContentResolver)
	 * @param syncResult Résultats (SyncResult) de synchronisation pour le syncadapter
	 * @throws AuthenticatorException Erreur d'authentification
	 * @throws IOException Erreur réseau
	 */
	public static <T extends Entity> void performSync(final Class<T> clzz, final Account account, final boolean fullSync, final long lastSync, final ContentResolver cr, final SyncResult syncResult) throws AuthenticatorException, IOException {
		Log.v(TAG+"."+clzz.getSimpleName(), "-> performSync: " + clzz.getName().substring(clzz.getName().lastIndexOf(".") + 1) + ", fullSync: " + fullSync);
		performRemoteDelete(clzz, account, cr, syncResult);
		performRemoteInsert(clzz, account, cr, syncResult);
		performRemoteUpdate(clzz, account, cr, syncResult);
		if (fullSync || lastSync > 0)
			performLocalSync(clzz, account, cr, syncResult, lastSync);
	}

	private static <T extends Entity> void performRemoteDelete(final Class<T> clzz, final Account account, final ContentResolver cr, final SyncResult syncResult) throws AuthenticatorException, IOException {
		try {
			Method m = clzz.getMethod("getMarkedAs", Account.class, T.MarkedAs.class);
			List<T> deleted = (List<T>) m.invoke(null, account, T.MarkedAs.DELETED);
			for (T delete : deleted) {
				Log.v(TAG + "." + clzz.getSimpleName(), "--> delete from remote: " + delete);
				if (RTDbApi.getInstance().delete(account, delete)) {
					Uri uri = delete.getUri().buildUpon().appendQueryParameter(Constants.CALLER_IS_SYNCADAPTER, "true").build();
					cr.delete(uri, null, null);
					syncResult.stats.numDeletes++;
				}
			}
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethodException" + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException" + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccessException" + e.getMessage(), e);
		} catch (InvocationTargetException e) {
			Log.e(TAG, "InvocationTargetException" + e.getMessage(), e);
		}
	}

	private static <T extends Entity> void performRemoteInsert(final Class<T> clzz, final Account account, final ContentResolver cr, final SyncResult syncResult) throws AuthenticatorException, IOException {
		Log.v(TAG+"."+clzz.getSimpleName(), "-> performRemoteInsert");
		try {
			Method m = clzz.getMethod("getMarkedAs", Account.class, T.MarkedAs.class);
			List<T> inserts = (List<T>) m.invoke(null, account, T.MarkedAs.INSERT);
			for (T insert : inserts) {
				Log.v(TAG+"."+clzz.getSimpleName(), "--> insert to remote: " + insert);
				boolean uploaded = true;
				int sourceId = 0;
				if (insert instanceof WithData) {
					WithData data = (WithData)insert;
					Log.v(TAG+"."+clzz.getSimpleName(), "--> upload file: " + data.getData() + ", size: " + data.getSize());
					uploaded = RTDbApi.getInstance().upload(account, data);
					Log.v(TAG+"."+clzz.getSimpleName(), "--> uploaded: " + uploaded);
				}
				if (uploaded)
					sourceId = RTDbApi.getInstance().save(account, insert);
				Log.v(TAG+"."+clzz.getSimpleName(), "--> sourceId: " + sourceId);
				if (sourceId > 0) {
					Uri uri = insert.getUri().buildUpon().appendQueryParameter(Constants.CALLER_IS_SYNCADAPTER, "true").build();
					ContentValues values = new ContentValues();
					values.put(BaseColumns.SOURCE_ID, sourceId);
					Log.v(TAG+"."+clzz.getSimpleName(), "update source id: " + values + " to uri: " + uri);
					cr.update(uri, values, null, null);
					syncResult.stats.numInserts++;
				}
			}
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethodException" + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException" + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccessException" + e.getMessage(), e);
		} catch (InvocationTargetException e) {
			Log.e(TAG, "InvocationTargetException" + e.getMessage(), e);
		}
	}

	private static <T extends Entity> void performRemoteUpdate(final Class<T> clzz, final Account account, final ContentResolver cr, final SyncResult syncResult) throws AuthenticatorException, IOException {
		try {
			Method m = clzz.getMethod("getMarkedAs", Account.class, T.MarkedAs.class);
			List<T> updates = (List<T>) m.invoke(null, account, T.MarkedAs.UPDATE);
			//Log.v(TAG, "-> updates: " + updates.size());
			for (T update : updates) {
				Log.v(TAG+"."+clzz.getSimpleName(), "--> update to remote: " + update);
				boolean uploaded = true;
				int sourceId = 0;
				if (update instanceof WithData) {
					WithData data = (WithData)update;
					WithData remote = (WithData) RTDbApi.getInstance().load(clzz, account, update.getSourceId());
					if (data.getSize() > 0 && data.getSize() != remote.getSize())
						uploaded = RTDbApi.getInstance().upload(account, data);
				}
				if (uploaded)
					sourceId = RTDbApi.getInstance().save(account, update);
				Log.v(TAG, "--> sourceId: " + sourceId);
				if (sourceId > 0) {
					Uri uri = update.getUri().buildUpon().appendQueryParameter(Constants.CALLER_IS_SYNCADAPTER, "true").build();
					ContentValues values = new ContentValues();
					values.put(BaseColumns.DIRTY, 0);
					cr.update(uri, values, null, null);
					syncResult.stats.numUpdates++;
				}
			}
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethodException" + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException" + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccessException" + e.getMessage(), e);
		} catch (InvocationTargetException e) {
			Log.e(TAG, "InvocationTargetException" + e.getMessage(), e);
		}
	}

	private static <T extends Entity> void performLocalSync(final Class<T> clzz, final Account account, final ContentResolver cr, final SyncResult syncResult, long lastSync) throws AuthenticatorException, IOException {
		try {
			Method m;
			m = clzz.getMethod("select", Account.class);
			List<T> currents = (List<T>) m.invoke(null, account);
			Log.v(TAG+"."+clzz.getSimpleName(), "currents size: " + currents.size());
			HashMap<String, String> args = new HashMap<>();
			if (lastSync > 0)
				args.put(Constants.SERVER_LAST_SYNC.toLowerCase(), String.valueOf(lastSync));
			List<T> remotes = RTDbApi.getInstance().load(clzz, account, args);
			Log.v(TAG+"."+clzz.getSimpleName(), "remotes size: " + remotes.size());
			for (T remote : remotes) {
				currents.remove(remote);
				m = clzz.getMethod("contains", Account.class, clzz);
				long localID = Long.parseLong(String.valueOf(m.invoke(null, account, remote)));
				if (localID == 0) {
					performLocalInsert(clzz, account, remote, syncResult);
				} else {
					remote.setId(localID);
					performLocalUpdate(clzz, account, remote, syncResult);
				}
			}
			//Delete local removed on remote
			if (currents.size() > 0) {
				for (final T current : currents) {
					Log.v(TAG, "--> deleted local: " + current + " (removed on remote)");
					m = clzz.getMethod("delete", Account.class, long.class, boolean.class);
					int cnt = (Integer) m.invoke(null, account, current.getId(), true);
					if (cnt > 0) syncResult.stats.numDeletes++;
				}
			}
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethodException" + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException" + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccessException" + e.getMessage(), e);
		} catch (InvocationTargetException e) {
			Log.e(TAG, "InvocationTargetException" + e.getMessage(), e);
		}
	}

	public static <T extends Entity> void performLocalInsert(final Class<T> clzz, final Account account, final Entity remote) throws AuthenticatorException, IOException {
		performLocalInsert(clzz, account, remote, null);
	}
	private static <T extends Entity> void performLocalInsert(final Class<T> clzz, final Account account, final Entity remote, final SyncResult syncResult) throws AuthenticatorException, IOException {
		Log.v(TAG+"."+clzz.getSimpleName(), "performLocalInsert");
		try {
			Log.v(TAG+"."+clzz.getSimpleName(), "-> insert to local: " + remote);
			Log.v(TAG+"."+clzz.getSimpleName(), "--> with data: " + (remote instanceof WithData));
			Method m;
			Uri uri;
			if (remote instanceof WithData) {
				WithData data = (WithData) remote;
				Uri file = null;
				Log.v(TAG+"."+clzz.getSimpleName(), "--> file size: " + data.getSize());
				if (data.getSize() > 0) file = RTDbApi.getInstance().download(account, data);
				m = clzz.getMethod("insert", Account.class, clzz, Uri.class, boolean.class);
				uri = (Uri) m.invoke(null, account, remote, file, true);
			} else {
				m = clzz.getMethod("insert", Account.class, clzz, boolean.class);
				uri = (Uri) m.invoke(null, account, remote, true);
			}
			if (uri != null && syncResult != null) syncResult.stats.numInserts++;
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethodException" + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException" + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccessException" + e.getMessage(), e);
		} catch (InvocationTargetException e) {
			Log.e(TAG, "InvocationTargetException" + e.getMessage(), e);
		}
	}

	public static <T extends Entity> void performLocalUpdate(final Class<T> clzz, final Account account, final Entity remote) throws AuthenticatorException, IOException {
		performLocalUpdate(clzz, account, remote, null);
	}
	private static <T extends Entity> void performLocalUpdate(final Class<T> clzz, final Account account, final Entity remote, final SyncResult syncResult) throws AuthenticatorException, IOException {
		try {
			Method m = clzz.getMethod("select", Uri.class);
			T local = (T) m.invoke(null, remote.getUri());
			Log.v(TAG+"."+clzz.getSimpleName(), "--> update local: " + local + ", remote: " + remote);
			m = clzz.getMethod("hasChange", clzz, clzz);
			boolean change = (Boolean) m.invoke(null, local, remote);
			boolean fileChange = false;
			if (remote instanceof WithData) {
				fileChange = ((WithData) local).getSize() != ((WithData) remote).getSize();
				// Log.v(TAG+"."+clzz.getSimpleName(), "local: " + localSize + ", remote: " + remoteSize + ", change: " + fileChange);
				change = change || fileChange;
			}
			Log.v(TAG+"."+clzz.getSimpleName(), "change: " + change + ", fileChange: " + fileChange);
			if (change) {
				Log.v(TAG+"."+clzz.getSimpleName(), "--> update to local: " + remote);
				int cnt;
				if (fileChange) {
					WithData data = (WithData) remote;
					Uri file = null;
					if (data.getSize() > 0) file = RTDbApi.getInstance().download(account, data);
					m = clzz.getMethod("update", Account.class, clzz, Uri.class, boolean.class);
					cnt = (Integer) m.invoke(null, account, remote, file, true);
				} else {
					m = clzz.getMethod("update", Account.class, clzz, boolean.class);
					cnt = (Integer) m.invoke(null, account, remote, true);
				}
				if (cnt > 0 && syncResult != null) syncResult.stats.numUpdates++;
			}
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethodException" + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException" + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccessException" + e.getMessage(), e);
		} catch (InvocationTargetException e) {
			Log.e(TAG, "InvocationTargetException" + e.getMessage(), e);
		}
	}
}
