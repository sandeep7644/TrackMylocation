package com.android.trackmylocation;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.adam.gpsstatus.GpsStatusProxy;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.AppBarLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import timber.log.Timber;

public class ActivityDashboard extends AppCompatActivity implements RecyclerviewAdapterActivities.ActivityClickListener {

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private MapView mapView;
    private GoogleMap mMap;
    private boolean zoomable;
    private Handler handlerOnUIThread;
    private Timer zoomBlockingTimer;
    private RecyclerviewAdapterActivities recyclerviewAdapterActivities;
    RecyclerView recyclerView;
    private TransitionUpdatesReciever transitonReciever;
    private LocationUpdatesReciver locationUpdatesReciver;
    private LatLng prevLatLng;
    LatLng currentLatLng;
    SharedPreferences sharedPreferences;
    private Marker marker;
    private Context mContext;
    private ActivityRecognitionClient mActivityRecognitionClient;
    private Intent mIntentBroadCastRec;
    private PendingIntent mPendingIntent;
    DateFormat DFormat
            = DateFormat.getDateTimeInstance(
            DateFormat.LONG, DateFormat.LONG,
            Locale.getDefault());
    private PackageInfo packageInfo;
    private File direct;
    ForegroundLocationUpdatesService mService;
    GpsStatusProxy proxy;
    MedianFilter medialFilter;
    private boolean mBound;
    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.M)

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ForegroundLocationUpdatesService.LocalBinder binder = (ForegroundLocationUpdatesService.LocalBinder) service;
            mService = binder.getService();
//            mService.requestLocationUpdates();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences("ActivityDashboardSharedPreferences", MODE_PRIVATE);
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        transitonReciever = new TransitionUpdatesReciever();
        LocalBroadcastManager.getInstance(this).registerReceiver(transitonReciever,
                new IntentFilter("Action_NEW_TRANSITION"));
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        proxy = GpsStatusProxy.getInstance(getApplicationContext());
        locationUpdatesReciver = new LocationUpdatesReciver();
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdatesReciver,
                new IntentFilter("Action_FOUND_LOCATION"));
//        if(!sharedPreferences.getBoolean("requestingActivityUpdates",false)){
//
//        }

//        Intent intent = new Intent(ActivityDashboard.this, BackgroundDetectedActivitiesService.class);
//        startService(intent);
        direct = new File(Environment.getExternalStorageDirectory() + "/Exam Creator");

        if (!direct.exists()) {
            if (direct.mkdir()) {
                //directory is created;
            }

        }
        mContext = ActivityDashboard.this;
        recyclerviewAdapterActivities = new RecyclerviewAdapterActivities(this);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(ActivityDashboard.this));
        recyclerView.setAdapter(recyclerviewAdapterActivities);
        updateList();

        mapView = this.findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
//                updateScreen(new LatLng(22.717821,75.872883));
                mMap.getUiSettings().setZoomControlsEnabled(false);
                mMap.getUiSettings().setCompassEnabled(true);
//                mMap.getUiSettings().setMyLocationButtonEnabled(true);
//                mMap.setMyLocationEnabled(true);
                mMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    @Override
                    public void onCameraMoveStarted(int reason) {
                        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {

                            zoomable = false;
                            if (zoomBlockingTimer != null) {
                                zoomBlockingTimer.cancel();
                            }

                            handlerOnUIThread = new Handler();

                            TimerTask task = new TimerTask() {
                                @Override
                                public void run() {
                                    handlerOnUIThread.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            zoomBlockingTimer = null;
                                            zoomable = true;

                                        }
                                    });
                                }
                            };
                            zoomBlockingTimer = new Timer();
                            zoomBlockingTimer.schedule(task, 10 * 1000);
                        }
                    }
                });
            }
        });
        AppBarLayout mAppBarLayout = (AppBarLayout) findViewById(R.id.appBar);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mAppBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior = new AppBarLayout.Behavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(AppBarLayout appBarLayout) {
                return false;
            }
        });
        params.setBehavior(behavior);

    }

    private void updateList() {
        recyclerviewAdapterActivities.updateList(OfflineEntries.getAppDatabase(getApplicationContext()).transitionDao().getAllOfflineGrnList());
    }

    private FusedLocationProviderClient getLocationClient() {
        if (mFusedLocationClient == null)
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);

        return mFusedLocationClient;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onStart() {
        super.onStart();
        if (this.mapView != null) {
            this.mapView.onStart();
        }
        bindService(new Intent(this, ForegroundLocationUpdatesService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);

    }

    @Override
    public void onActivityItemClicked(TransitionEntity transitionEntity) {
        Intent i = new Intent(ActivityDashboard.this, LocationDetailsActivity.class);
        i.putExtra("detail_id", transitionEntity.getMillisecondsatActivitychange());
        startActivity(i);
    }

    @SuppressLint("MissingPermission")
    public void requestLocationUpdates(View view) {
//        TransitionEntity transitionEntity = new TransitionEntity();
//        transitionEntity.setActivity_type(DFormat.format(Calendar.getInstance().getTime()));
//        transitionEntity.setDate_time("");
//        transitionEntity.setMillisecondsatActivitychange(String.valueOf(System.currentTimeMillis()));
//        sharedPreferences.edit().putString("unique_id", transitionEntity.getMillisecondsatActivitychange()).commit();
//        OfflineEntries.getAppDatabase(getApplicationContext()).transitionDao().insertDirectGrnOffline(transitionEntity);
//        Intent intent_transition_update = new Intent("Action_NEW_TRANSITION");
//        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent_transition_update);
//        Intent intent = new Intent(mContext, LocationUpdatesBroadcastReceiver.class);
//        intent.setAction(LocationUpdatesBroadcastReceiver.LOCATION_RECEIVER_ACTION);
//        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        LocationRequest mLocationRequest = new LocationRequest();
//        mLocationRequest.setInterval(2000);
//        mLocationRequest.setFastestInterval(2000);
//        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//        mLocationRequest.setMaxWaitTime(2000);
//        getLocationClient().requestLocationUpdates(mLocationRequest, pendingIntent);
        mService.requestLocationUpdates();
        setActivityTransition();

    }

    public void removeLocationUpdates(View view) {
//        Intent intent = new Intent(mContext, LocationUpdatesBroadcastReceiver.class);
//        intent.setAction(LocationUpdatesBroadcastReceiver.LOCATION_RECEIVER_ACTION);
//        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        getLocationClient().removeLocationUpdates(pendingIntent);

        mService.removeLocationUpdates();

    }

    private class TransitionUpdatesReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateList();

        }
    }


    private class LocationUpdatesReciver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra("latitude", 0);
            double longitude = intent.getDoubleExtra("longitude", 0);
            setMarker(new LatLng(latitude, longitude));


        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.navigationdrawer_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {

            case R.id.clear:
                OfflineEntries.getAppDatabase(getApplicationContext()).transitionDao().deleteAllLogs();
                OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().deleteAllLogs();
                sharedPreferences.edit().remove("requestingActivityUpdates").commit();
                updateList();
                break;


            case R.id.go_to_details:
                Intent i = new Intent(ActivityDashboard.this, LocationDetailsActivity.class);
                i.putExtra("detail_id", "14 November 2019%");
                startActivity(i);
                break;
            case R.id.exportdatabase:
                exportDB();
                break;


        }
        return super.onOptionsItemSelected(item);
    }

    private void setMarker(LatLng latLng) {
        if (mMap == null)
            return;

        //Zoom in and animate the camera.

        if (prevLatLng == null) {
            updateScreen(latLng);
            prevLatLng = latLng;
            return;
        }

        Location startPoint = new Location("locationA");
        startPoint.setLatitude(prevLatLng.latitude);
        startPoint.setLongitude(prevLatLng.longitude);

        Location endPoint = new Location("locationB");
        endPoint.setLatitude(latLng.latitude);
        endPoint.setLongitude(latLng.longitude);

        double distance = startPoint.distanceTo(endPoint);
        if (distance >= 10) {
            PolylineOptions rectLine = new PolylineOptions()
                    .width(10)
                    .color(getResources().getColor(R.color.colorPrimary));
            updateScreen(latLng);
            rectLine.add(prevLatLng);
            rectLine.add(latLng);
            mMap.addPolyline(rectLine);
        }
        prevLatLng = latLng;


        // Placing a marker on the touched position
    }

    private void updateScreen(LatLng latLng) {
        if (marker != null) {
            marker.remove();
        }
        marker = mMap.addMarker(new MarkerOptions().position(latLng).title("Changed location"));
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latLng)
                .zoom(17).build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public void onPause() {
        super.onPause();

        if (this.mapView != null) {
            this.mapView.onPause();
        }
    }


    @Override
    public void onResume() {
        super.onResume();

        if (this.mapView != null) {
            this.mapView.onResume();
        }

    }

    @Override
    protected void onDestroy() {
        if (this.mapView != null) {
            this.mapView.onDestroy();
        }
//        if (transitonReciever != null) {
//            LocalBroadcastManager.getInstance(this).unregisterReceiver(transitonReciever);
//        }
//        if (locationUpdatesReciver != null) {
//            LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdatesReciver);
//        }
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onStop() {
        super.onStop();
        if (this.mapView != null) {
            this.mapView.onStop();
        }
        if (mBound) {
//             Unbind from the service. This signals to the service that this activity is no longer
//             in the foreground, and the service can respond by promoting itself to a foreground
//             service.
            unbindService(mServiceConnection);
            mBound = false;
        }
    }


    private PendingIntent getTransitionPendingIntent() {
        Intent intent = new Intent(this, TransitionUpdatesBroadcastReceiver.class);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setAction(TransitionUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //exporting database
    private void exportDB() {
        // TODO Auto-generated method stub

        try {
            File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            if (sd.canWrite()) {
                String currentDBPath = "/data/" + packageInfo.packageName

                        + "/databases/" + "OFFLINE_DATABASE";
                File currentDB = new File(data, currentDBPath);
                File backupdb = new File(direct, String.valueOf(System.currentTimeMillis()));

                FileChannel src = new FileInputStream(currentDB).getChannel();
                FileChannel dst = new FileOutputStream(backupdb).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
                Toast.makeText(getBaseContext(), backupdb.toString(),
                        Toast.LENGTH_LONG).show();

            }
        } catch (Exception e) {

            Toast.makeText(getBaseContext(), e.toString(), Toast.LENGTH_LONG)
                    .show();

        }
    }

    void setActivityTransition() {
        List<ActivityTransition> transitions = new ArrayList<>();

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

//        transitions.add(
//                new ActivityTransition.Builder()
//                        .setActivityType(DetectedActivity.IN_VEHICLE)
//                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
//                        .build());

//        transitions.add(
//                new ActivityTransition.Builder()
//                        .setActivityType(DetectedActivity.WALKING)
//                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
//                        .build());
        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());
//        transitions.add(
//                new ActivityTransition.Builder()
//                        .setActivityType(DetectedActivity.STILL)
//                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
//                        .build());
        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.STILL)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());


        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.RUNNING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());
        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.ON_FOOT)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

//        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
//        Task<Void> task = ActivityRecognition.getClient(getApplicationContext())
//                .requestActivityTransitionUpdates(request, getTransitionPendingIntent());
//
//        task.addOnSuccessListener(
//                new OnSuccessListener<Void>() {
//                    @Override
//                    public void onSuccess(Void result) {
//                        sharedPreferences.edit().putBoolean("requestingActivityUpdates", true).commit();
//                        // Handle success
//                    }
//                }
//        );
//
//        task.addOnFailureListener(
//                new OnFailureListener() {
//                    @Override
//                    public void onFailure(Exception e) {
//                        // Handle error
//                    }
//                }
//        );

        Intent intent = new Intent(this, ForegroundLocationUpdatesService.class);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        mPendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mActivityRecognitionClient = ActivityRecognition.getClient(this);


        Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                10000,
                mPendingIntent);

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(mContext, "Successfully requested activity updates", Toast.LENGTH_SHORT).show();
                Timber.i("Successfully requested activity updates");


            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(mContext, "failed requested activity updates", Toast.LENGTH_SHORT).show();
                Timber.i("Successfully requested activity updates");

            }
        });

    }


}