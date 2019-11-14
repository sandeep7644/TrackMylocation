package com.android.trackmylocation;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class LocationDetailsActivity extends AppCompatActivity {

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private MapView mapView;
    private GoogleMap mMap;
    private boolean zoomable;
    private Handler handlerOnUIThread;
    private Timer zoomBlockingTimer;
    private RecyclerviewAdapterLocation recyclerviewAdapterLocation;
    RecyclerView recyclerView;
    private LatLng prevLatLng;
    LatLng currentLatLng;
    String detail_id;
    private PolylineOptions rectLine;
    Location startPoint = new Location("locationA");
    Location endPoint = new Location("locationB");
    private BitmapDescriptor icon;
    private Timer timer;
//i pushed this file for testing
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_details);
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        detail_id = getIntent().getStringExtra("detail_id");
        recyclerviewAdapterLocation = new RecyclerviewAdapterLocation();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setVisibility(View.GONE);
        recyclerView.setLayoutManager(new LinearLayoutManager(LocationDetailsActivity.this));
//        recyclerView.setAdapter(recyclerviewAdapterLocation);

        icon = bitmapDescriptorFromVector(LocationDetailsActivity.this, R.drawable.round_marker);

        mapView = this.findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                updateList();
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

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, @DrawableRes int vectorDrawableResourceId) {
        Drawable background = ContextCompat.getDrawable(context, R.drawable.round_marker);
        background.setBounds(0, 0, background.getIntrinsicWidth(), background.getIntrinsicHeight());
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorDrawableResourceId);
        vectorDrawable.setBounds(40, 20, vectorDrawable.getIntrinsicWidth() + 40, vectorDrawable.getIntrinsicHeight() + 20);
        Bitmap bitmap = Bitmap.createBitmap(background.getIntrinsicWidth(), background.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background.draw(canvas);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void updateList() {
//        recyclerviewAdapterLocation.updateList(OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id));
        drawOnMap();
    }

    private void drawOnMap() {
        List<LocationEntity> locationEntitieswithout_filter = OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id, "without_filter");
        List<LocationEntity> kalman_filter = OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id, "predicted_filter");
        List<LocationEntity> normallocation = OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id, "normallocation");
        drawPolyLines(locationEntitieswithout_filter);
        drawPolyLines(kalman_filter);
        drawPolyLines(normallocation);
    }

    private void drawPolyLines(List<LocationEntity> list) {
        if (list == null)
            return;

        if (list.size() == 0)
            return;

        if (list.size() == 1) {
            final LatLng stop = new LatLng(list.get(0).getLatitude(), list.get(0).getLongitude());
            updateScreen(stop);

            return;
        }
//        if (list.size() <= 2)
//            return;
        rectLine = new PolylineOptions()
                .color(getResources().getColor(R.color.colorPrimary))
                .width(7);
        final LatLng source = new LatLng(list.get(list.size() - 1).getLatitude(), list.get(list.size() - 1).getLongitude());
        final LatLng destination = new LatLng(list.get(0).getLatitude(), list.get(0).getLongitude());


        mMap.addMarker(new MarkerOptions().position(source));
        mMap.addMarker(new MarkerOptions().position(destination));
        LatLng prevLatLng = null;
        //sandeep  dubey

        for (int i = 0; i < list.size(); i++) {
            LocationEntity locationEntity = list.get(i);
            if (locationEntity.getAddress().equalsIgnoreCase("without_filter")) {
                rectLine.color(getResources().getColor(R.color.withfilterpolylinecolor));

            } else if (locationEntity.getAddress().equalsIgnoreCase("normallocation")) {
                rectLine.color(getResources().getColor(R.color.activity_main_label_kal));
            } else if (locationEntity.getAddress().equalsIgnoreCase("predicted_filter")) {
                rectLine.color(getResources().getColor(R.color.colorExitItem));
            }
            LatLng latLng = new LatLng(locationEntity.getLatitude(), locationEntity.getLongitude());
            if (prevLatLng == null) {
                prevLatLng = latLng;
            } else {
                startPoint.setLatitude(prevLatLng.latitude);
                startPoint.setLongitude(prevLatLng.longitude);
                endPoint.setLatitude(latLng.latitude);
                endPoint.setLongitude(latLng.longitude);

//                if (startPoint.distanceTo(endPoint) >= 20) {

//testing
                rectLine.startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .jointType(JointType.ROUND)
                        .add(prevLatLng)
                        .add(latLng);
//                }

                prevLatLng = latLng;
            }


        }

        mMap.addPolyline(rectLine);

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().include(source).include(destination).build(), 100));

            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (this.mapView != null) {
            this.mapView.onStart();
        }


    }


    private void updateScreen(LatLng latLng) {
//        hello
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng).title("Changed location"));
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

        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.mapView != null) {
            this.mapView.onStop();
        }
    }


}