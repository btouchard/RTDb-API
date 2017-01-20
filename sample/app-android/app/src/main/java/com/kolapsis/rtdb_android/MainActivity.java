package com.kolapsis.rtdb_android;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.kolapsis.rtdb_android.helpers.AccountHelper;
import com.kolapsis.rtdb_android.helpers.DataHelper;
import com.kolapsis.rtdb_android.helpers.DatabaseHelper;
import com.kolapsis.rtdb_android.providers.CategoryProvider;
import com.kolapsis.rtdb_android.services.RTDbApi;
import com.kolapsis.rtdb_android.services.RTDbService;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = Constants.APP_NAME + ".MainActivity";

    private CategoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatabaseHelper.invalidate(this);
        AccountHelper.setContext(this);
        if (!AccountHelper.hasAccount()) {
            showNoAccount();
        } else if (permsGranted()) {
            showApp();
        }
    }

    private boolean permsGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (shouldShowRequestPermissionRationale(WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(new View(this), R.string.permission_files, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        @TargetApi(Build.VERSION_CODES.M)
                        public void onClick(View v) {
                            requestPermissions(new String[]{WRITE_EXTERNAL_STORAGE}, Constants.RESULT_REQUEST_PERMS);
                        }
                    });
        } else {
            requestPermissions(new String[]{WRITE_EXTERNAL_STORAGE}, Constants.RESULT_REQUEST_PERMS);
        }
        return false;
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == Constants.RESULT_REQUEST_PERMS) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showApp();
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, RTDbService.class));
        getSupportLoaderManager().destroyLoader(0);
        super.onDestroy();
    }

    private void showNoAccount() {
        setContentView(R.layout.activity_main_no_account);
        findViewById(R.id.add_account).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNewAccount();
            }
        });
        openNewAccount();
    }
    private void openNewAccount() {
        Intent intent = AccountManager.newChooseAccountIntent(null, null, new String[]{Constants.ACCOUNT_TYPE}, null, Constants.AUTHTOKEN_TYPE, null, null);
        startActivityForResult(intent, Constants.RESULT_NEW_ACCOUNT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constants.RESULT_NEW_ACCOUNT:
                showApp();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showApp() {
        Log.v(TAG, "showApp");
        startService(new Intent(this, RTDbService.class));
        setContentView(R.layout.activity_main);
        RecyclerView list = (RecyclerView) findViewById(R.id.list);
        RecyclerView.LayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        list.setLayoutManager(lm);
        adapter = new CategoryAdapter(this);
        list.setAdapter(adapter);
        getSupportLoaderManager().initLoader(0, null, loaderCallback);
        ContentResolver.requestSync(AccountHelper.getAccount(), CategoryProvider.AUTHORITY, getSyncExtras());
        // checkCategory();
        // checkInsert();
    }
    private void checkInsert() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Account account = AccountHelper.getAccount();
                try {
                    JSONObject json = new JSONObject("{'title':'My test category'}");
                    DataHelper.Category c = DataHelper.Category.from(account, json);
                    c.setData("alf.jpg");
                    c.setSize(48331);
                    String url = RTDbApi.getInstance().getApiUrl() + "/medias/source/alf.jpg";
                    Uri file = RTDbApi.getInstance().download(account, url);
                    DataHelper.Category.insert(account, c, file);
                    ContentResolver.requestSync(AccountHelper.getAccount(), CategoryProvider.AUTHORITY, getSyncExtras());
                } catch (JSONException | AuthenticatorException | IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }).start();
    }
    private void checkCategory() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(DataHelper.Category.CONTENT_URI, null, null, null, null);
        if (c.moveToFirst()) {
            for (int i=0; i<c.getColumnCount(); i++) {
                Log.v(TAG, i + ") " + c.getColumnName(i) + " => " + c.getString(i));
            }
        }
    }
    private Bundle getSyncExtras() {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
        return extras;
    }

    private LoaderManager.LoaderCallbacks<Cursor> loaderCallback = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new DataHelper.Category.Loader(getApplicationContext(), AccountHelper.getAccount(), null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            adapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            adapter.swapCursor(null);
        }
    };

    public static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        public static final String TAG = MainActivity.TAG + ".CategoryAdapter";

        public static class ViewHolder extends RecyclerView.ViewHolder {
            private TextView title;
            private ImageView image;
            public ViewHolder(View view) {
                super(view);
                title = (TextView) view.findViewById(R.id.title);
                image = (ImageView) view.findViewById(R.id.img);
            }
        }

        private Context context;
        private Cursor cursor;

        public CategoryAdapter(Context ctx) {
            context = ctx;
        }

        public void swapCursor(Cursor c) {
            cursor = c;
            notifyDataSetChanged();
        }

        public DataHelper.Category getItem(int position) {
            cursor.moveToPosition(position);
            return DataHelper.Category.cursorToCategory(cursor);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.category_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final DataHelper.Category category = getItem(position);
            holder.title.setText(category.getTitle());
            if (!TextUtils.isEmpty(category.getFileName()))
                holder.image.setImageURI(category.getUri());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new AlertDialog.Builder(context)
                            .setTitle("Supprimer ?")
                            .setMessage("Supprimer la catégorie: " + category.getTitle())
                            .setNegativeButton("Annuler", null)
                            .setPositiveButton("Supprimer", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                boolean success = RTDbApi.getInstance().delete(AccountHelper.getAccount(), category);
                                                Log.v(TAG, "Catégorie #" + category.getId() + " " + category.getTitle() + " supprimé: " + success);
                                            } catch (AuthenticatorException | IOException e) {
                                                Log.e(TAG, e.getMessage(), e);
                                            }
                                        }
                                    }).start();
                                }
                            })
                            .create().show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return cursor == null ? 0 : cursor.getCount();
        }
    }
}
