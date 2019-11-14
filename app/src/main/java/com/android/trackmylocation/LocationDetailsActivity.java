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
import android.widget.CheckBox;
import android.widget.CompoundButton;

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
import com.google.android.gms.maps.model.Polyline;
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
    //change test
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
    private PolylineOptions rectLineNormalOptions, rectlinePredictedOptions, rectlineafterFilterOptions;
    private Polyline rectLineNormal, rectlinePredicted, rectlineafterFilter;
    Location startPoint = new Location("locationA");
    Location endPoint = new Location("locationB");
    CheckBox checknormal, checkfilter, checkpredicted;

    //i pushed this file for testing
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_details);
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        detail_id = getIntent().getStringExtra("detail_id");
        checknormal = findViewById(R.id.checknormal);
        checkfilter = findViewById(R.id.checkfilter);
        checkpredicted = findViewById(R.id.checkpredicted);
        rectLineNormalOptions = new PolylineOptions()
                .color(getResources().getColor(R.color.activity_main_label_kal))
                .width(7);
        rectlinePredictedOptions = new PolylineOptions()
                .color(getResources().getColor(R.color.colorExitItem))
                .width(7);
        rectlineafterFilterOptions = new PolylineOptions()
                .color(getResources().getColor(R.color.withfilterpolylinecolor))
                .width(7);

        checknormal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (rectLineNormal == null) {
                    drawPolyLinesNormal();
                } else {
                    if (b) {
                        rectLineNormal = mMap.addPolyline(rectLineNormalOptions);
                    } else {
                        rectLineNormal.remove();
                    }
                }

            }
        });
        checkfilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (rectlineafterFilter == null) {
                    drawPolyLinesFiltered();
                } else {
                    if (b) {
                        rectlineafterFilter = mMap.addPolyline(rectlineafterFilterOptions);
                    } else {
                        rectlineafterFilter.remove();
                    }
                }
            }
        });
        checkpredicted.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (rectlinePredicted == null) {
                    drawPolyLinesPredicted();
                } else {
                    if (b) {
                        rectlinePredicted = mMap.addPolyline(rectlinePredictedOptions);
                    } else {
                        rectlinePredicted.remove();
                    }
                }
            }
        });
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

    }



    private void updateList() {
//        recyclerviewAdapterLocation.updateList(OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id));
    }


    private void drawPolyLinesPredicted() {
        List<LocationEntity> list = OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id, "predicted_filter");

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

        final LatLng source = new LatLng(list.get(list.size() - 1).getLatitude(), list.get(list.size() - 1).getLongitude());
        final LatLng destination = new LatLng(list.get(0).getLatitude(), list.get(0).getLongitude());


        mMap.addMarker(new MarkerOptions().position(source));
        mMap.addMarker(new MarkerOptions().position(destination));
        LatLng prevLatLng = null;
        //sandeep  dubey

        for (int i = 0; i < list.size(); i++) {
            LocationEntity locationEntity = list.get(i);

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
                rectlinePredictedOptions.startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .jointType(JointType.ROUND)
                        .add(prevLatLng)
                        .add(latLng);
//                }

                prevLatLng = latLng;
            }


        }

        rectlinePredicted = mMap.addPolyline(rectlinePredictedOptions);

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().include(source).include(destination).build(), 100));

            }
        });

    }

    private void drawPolyLinesNormal() {
        List<LocationEntity> list = OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id, "normallocation");

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

        final LatLng source = new LatLng(list.get(list.size() - 1).getLatitude(), list.get(list.size() - 1).getLongitude());
        final LatLng destination = new LatLng(list.get(0).getLatitude(), list.get(0).getLongitude());


        mMap.addMarker(new MarkerOptions().position(source));
        mMap.addMarker(new MarkerOptions().position(destination));
        LatLng prevLatLng = null;
        //sandeep  dubey

        for (int i = 0; i < list.size(); i++) {
            LocationEntity locationEntity = list.get(i);

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
                rectLineNormalOptions.startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .jointType(JointType.ROUND)
                        .add(prevLatLng)
                        .add(latLng);
//                }

                prevLatLng = latLng;
            }


        }

        rectLineNormal = mMap.addPolyline(rectLineNormalOptions);

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().include(source).include(destination).build(), 100));

            }
        });

    }

    private void drawPolyLinesFiltered() {
        List<LocationEntity> list = OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().getLocationData(detail_id, "without_filter");
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

        final LatLng source = new LatLng(list.get(list.size() - 1).getLatitude(), list.get(list.size() - 1).getLongitude());
        final LatLng destination = new LatLng(list.get(0).getLatitude(), list.get(0).getLongitude());


        mMap.addMarker(new MarkerOptions().position(source));
        mMap.addMarker(new MarkerOptions().position(destination));
        LatLng prevLatLng = null;
        //sandeep  dubey

        for (int i = 0; i < list.size(); i++) {
            LocationEntity locationEntity = list.get(i);

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
                rectlineafterFilterOptions.startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .jointType(JointType.ROUND)
                        .add(prevLatLng)
                        .add(latLng);
//                }

                prevLatLng = latLng;
            }


        }

        rectlineafterFilter = mMap.addPolyline(rectlineafterFilterOptions);

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