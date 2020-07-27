package technology.nine.payo.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;
import technology.nine.payo.R;
import technology.nine.payo.adapter.AllSmsAdapter;
import technology.nine.payo.model.Sms;
import technology.nine.payo.utils.RxTextObservable;

public class MainActivity extends AppCompatActivity implements AllSmsAdapter.TagAddListener {

    private static final int PERMISSION_CALLBACK_CONSTANT = 100;
    private static final int REQUEST_PERMISSION_SETTING = 101;
    String[] permissionsRequired = new String[]{Manifest.permission.READ_SMS};
    private SharedPreferences permissionStatus;
    private boolean sentToSettings = false;
    private boolean login = false;
    private boolean background_location = false;
    private Realm realm;
    private double credited = 0;
    private double debited = 0;

    PieChart pieChart;
    private RecyclerView recyclerView;
    private AllSmsAdapter adapter;
    private RealmList<Sms> smsRealmList = new RealmList<>();
    RealmResults<Sms> realmResults;
    ProgressDialog progressDialog;
    private SearchView searchView;
    private BottomSheetDialog mBottomSheetDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pieChart = findViewById(R.id.pieChart);
        recyclerView = findViewById(R.id.recycler_view);

        adapter = new AllSmsAdapter(this, smsRealmList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);


        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        realm = Realm.getDefaultInstance();
        permissionStatus = getSharedPreferences("permissionStatus", MODE_PRIVATE);
        checkPermissions();


    }

    @SuppressLint("CheckResult")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        if (item != null) {
            searchView = (SearchView) item.getActionView();
        }
        searchView.setQueryHint("Search tag");
        searchView.animate();
        RxTextObservable.fromSearch(searchView)
                .debounce(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(query -> upDateList(query));
        return true;
    }

    public void getAllSms() {

        realm.executeTransactionAsync(bgRealm -> {
            credited = 0;
            debited = 0;

            ContentResolver cr = getContentResolver();
            Cursor c = cr.query(Uri.parse(Telephony.Sms.CONTENT_URI + "/inbox"), null, null, null, null);
            int totalSMS = 0;

            RealmList<Sms> smsModels = new RealmList<>();
            if (c != null) {
                totalSMS = c.getCount();


                if (c.moveToFirst()) {
                    for (int j = 0; j < totalSMS; j++) {
                        String smsDate = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                        String number = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                        String body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                        String id = c.getString(c.getColumnIndexOrThrow(Telephony.Sms._ID));

                        Date dateFormat = new Date(Long.valueOf(smsDate));
                        String type;
                        String add = c.getString(2);

                        Sms smsFinancial = bgRealm.where(Sms.class).equalTo("id", id).findFirst();
                        if (smsFinancial == null) {

                            //Pattern trasRegEx= Pattern.compile("\"(?=.*[Aa]ccount.*|.*[Aa]/[Cc].*|.*[Aa][Cc][Cc][Tt].*|.*[Cc][Aa][Rr][Dd].*)(?=.*[Cc]redit.*|.*[Dd]ebit.*)(?=.*[Ii][Nn][Rr].*|.*[Rr][Ss].*)\"");
                            Pattern trasRegEx = Pattern.compile("(?=.*[Aa]ccount.*|.*[Aa]/[Cc].*|.*[Aa][Cc][Cc][Tt].*|.*[Cc][Aa][Rr][Dd].*)(?=.*[Cc]redit.*|.*[Dd]ebit.*)(?=.*[Ii][Nn][Rr].*|.*[Rr][Ss].*)");
                            Matcher transMessage = trasRegEx.matcher(body);
                            if (transMessage.find()) {
                                if (body.contains("debited") || body.contains("withdrawn")) {

                                    Pattern regEx = Pattern.compile("(?i)(?:(?:RS|INR|MRP)\\.?\\s?)(\\d+(:?\\,\\d+)?(\\,\\d+)?(\\.\\d{1,2})?)");
                                    Matcher m = regEx.matcher(body);

                                    if (m.find()) {
                                        saveToDatabase(m.group(0), "debited", dateFormat, id, smsModels);

                                    }
                                } else if (body.contains("credited")) {
                                    Pattern regEx = Pattern.compile("(?i)(?:(?:RS|INR|MRP)\\.?\\s?)(\\d+(:?\\,\\d+)?(\\,\\d+)?(\\.\\d{1,2})?)");
                                    Matcher m = regEx.matcher(body);

                                    if (m.find()) {
                                        saveToDatabase(m.group(0), "credited", dateFormat, id, smsModels);
                                    }
                                }

                            }
                        }
                        c.moveToNext();
                    }
                }

                c.close();
                bgRealm.copyToRealmOrUpdate(smsModels);

            } else {
            }
        }, () -> {

            upDateList("");
        }, error -> {
            Log.e("yese", error.getLocalizedMessage());
        });

    }

    private void saveToDatabase(String amount, String type, Date date, String id, RealmList<Sms> smsModels) {
        try {
            amount = amount.replaceAll("rs", "");
            amount = amount.replaceAll("inr", "");
            amount = amount.replaceAll("INR", "");
            amount = amount.replaceAll(" ", "");
            amount = amount.replaceAll(",", "");
            amount = amount.replaceAll("Rs\\.", "");
            amount = amount.replaceAll("Rs", "");
            amount = amount.replaceAll(" ", "");
            Sms financialSms = new Sms();
            financialSms.setId(String.valueOf(id));
            financialSms.setAmount(Double.parseDouble(amount));
            financialSms.setType(type);
            financialSms.setDate(date);
            smsModels.add(financialSms);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    private boolean checkPermissions() {


        if (ActivityCompat.checkSelfPermission(this, permissionsRequired[0]) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsRequired[0])) {
                //Show Information about why you need the permission

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Need Read SMS Permissions");
                builder.setMessage("This app needs Read SMS Permissions.");
                builder.setPositiveButton("Grant", (dialog, which) -> {
                    dialog.cancel();
                    ActivityCompat.requestPermissions(MainActivity.this, permissionsRequired, PERMISSION_CALLBACK_CONSTANT);
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            } else if (permissionStatus.getBoolean(permissionsRequired[0], false)) {
                //Previously Permission Request was cancelled with 'Dont Ask Again',
                // Redirect to Settings after showing Information about why you need the permission
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Need Read SMS Permissions");
                builder.setMessage("This app needs Read SMS Permissions.");
                builder.setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        sentToSettings = true;
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", MainActivity.this.getPackageName(), null);
                        intent.setData(uri);
                        MainActivity.this.startActivityForResult(intent, REQUEST_PERMISSION_SETTING);

                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            } else {
                //just request the permission
                ActivityCompat.requestPermissions(this, permissionsRequired, PERMISSION_CALLBACK_CONSTANT);
            }

            SharedPreferences.Editor editor = permissionStatus.edit();
            editor.putBoolean(permissionsRequired[0], true);
            editor.apply();

            return false;
        } else {
            //You already have the permission, just go ahead.
            proceedAfterPermission();
            return true;
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CALLBACK_CONSTANT) {
            //check if all permissions are granted
            boolean allgranted = false;

            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    allgranted = true;
                } else {
                    allgranted = false;
                    break;
                }
            }
            if (allgranted) {
                proceedAfterPermission();
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsRequired[0])) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Need Read SMS Permissions");
                builder.setMessage("This app needs Read SMS permissions.");
                builder.setPositiveButton("Grant", (dialog, which) -> {
                    dialog.cancel();
                    ActivityCompat.requestPermissions(MainActivity.this, permissionsRequired, PERMISSION_CALLBACK_CONSTANT);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                builder.show();
            }


        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION_SETTING) {
            if (ActivityCompat.checkSelfPermission(this, permissionsRequired[0]) == PackageManager.PERMISSION_GRANTED) {
                //Got Permission
                proceedAfterPermission();
            } else {
                Log.e("yeswanth", "is called on activity");

            }
        }
    }

    private void proceedAfterPermission() {

        getAllSms();

    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (sentToSettings) {

            if (background_location) {
                if (ActivityCompat.checkSelfPermission(this, permissionsRequired[0]) == PackageManager.PERMISSION_GRANTED) {
                    //Got Permission
                    proceedAfterPermission();

                } else {
                    Log.e("yeswanth", "is called on postResume");
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, permissionsRequired[0]) == PackageManager.PERMISSION_GRANTED) {
                    //Got Permission
                    proceedAfterPermission();
                    //login = false;
                }
            }

        }
    }


    private void upDateList(String query) {

        if (query != null && !query.isEmpty()) {
            realmResults = realm.where(Sms.class).contains("tag", query, Case.INSENSITIVE).findAllAsync();
        } else {
            realmResults = realm.where(Sms.class).findAllAsync();
        }
        realmResults.addChangeListener(results -> {
           /* if (results.size() <= 0) {
                empty_layout.setVisibility(View.VISIBLE);
            } else {
                empty_layout.setVisibility(View.GONE);
            }*/
            smsRealmList.clear();
            smsRealmList.addAll(results);
            adapter.notifyDataSetChanged();

            credited = 0;
            debited = 0;
            for (Sms sms : results) {
                if (sms.getType().equals("credited")) {
                    credited += sms.getAmount();
                } else {
                    debited += sms.getAmount();
                }
            }
            if (credited == 0 && debited == 0) {
                pieChart.clear();
            } else {
                pieChart.clear();
                pieChart.setUsePercentValues(true);
                Description desc = new Description();
                desc.setText("Financial transactions based on SMS");

                pieChart.setDescription(desc);
                pieChart.setHoleRadius(10f);
                pieChart.setTransparentCircleRadius(10f);
                List<PieEntry> value = new ArrayList<>();
                value.add(new PieEntry((float) credited, "Total Income"));
                value.add(new PieEntry((float) debited, "Total Expenses"));
                PieDataSet pieDataSet = new PieDataSet(value, "");
                pieDataSet.setColors(ColorTemplate.MATERIAL_COLORS);
                PieData pieData = new PieData(pieDataSet);
                pieChart.setData(pieData);
            }
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                }
            }, 500);


        });
    }

    @Override
    public void onClick(String id) {
        addDialog(id);
    }

    private void addDialog(String id) {
        android.app.AlertDialog.Builder verifyDialogBuilder = new android.app.AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.add_tag_layout, null, true);
        verifyDialogBuilder.setView(rowView);
        verifyDialogBuilder.setCancelable(false);
        android.app.AlertDialog verifyDialog = verifyDialogBuilder.create();
        verifyDialog.show();
        TextView btCancel = rowView.findViewById(R.id.btCancel);
        TextView btSave = rowView.findViewById(R.id.btSave);
        EditText etTag = rowView.findViewById(R.id.etTag);


        btCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyDialog.cancel();
            }
        });


        btSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tag = etTag.getText().toString().trim();
                if (!tag.isEmpty()) {
                    verifyDialog.cancel();
                    realm.executeTransactionAsync(new Realm.Transaction() {
                        @Override
                        public void execute(Realm bgRealm) {
                            Sms sms = bgRealm.where(Sms.class).equalTo("id", id).findFirst();
                            if (sms != null) {
                                sms.setTag(tag);
                            }
                        }
                    }, () -> {
                        Toast.makeText(getApplicationContext(), "Successfully Tag is added", Toast.LENGTH_SHORT).show();
                        // Transaction was a success.
                    }, error -> {
                        Toast.makeText(getApplicationContext(), "Something went wrong, Please try again. ", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Toast.makeText(getApplicationContext(), "Please enter tag", Toast.LENGTH_SHORT).show();

                }


            }
        });
    }

}