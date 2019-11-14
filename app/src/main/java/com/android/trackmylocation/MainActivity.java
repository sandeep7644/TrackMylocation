/*
 * MainActivity
 *
 * Copyright (c) 2014 Renato Villone
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.android.trackmylocation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.trackmylocation.lib.KalmanLocationManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.AppBarLayout;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity {

    // Constant

    /**
     * Request location updates with the highest possible frequency on gps.
     * Typically, this means one update per second for gps.
     */
    private static final long GPS_TIME = 1000;

    /**
     * For the network provider, which gives locations with less accuracy (less reliable),
     * request updates every 5 seconds.
     */
    private static final long NET_TIME = 1000;

    /**
     * For the filter-time argument we use a "real" value: the predictions are triggered by a timer.
     * Lets say we want 5 updates (estimates) per second = update each 200 millis.
     */
    private static final long FILTER_TIME = 200;

    // Context
    private KalmanLocationManager mKalmanLocationManager;
    private SharedPreferences mPreferences;

    // UI elements
    private MapView mMapView;
    private TextView tvGps;
    private TextView tvNet;
    private TextView tvKal;
    private TextView tvAlt;
    private SeekBar sbZoom;

    // Map elements
    private GoogleMap mGoogleMap;
    private Circle mGpsCircle;
    private Circle mNetCircle;

    // Textview animation
    private Animation mGpsAnimation;
    private Animation mNetAnimation;
    private Animation mKalAnimation;

    // GoogleMaps own OnLocationChangedListener (not android's LocationListener)
    private LocationSource.OnLocationChangedListener mOnLocationChangedListener;
    private boolean zoomable;
    private Timer zoomBlockingTimer;
    private Handler handlerOnUIThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Context
        mKalmanLocationManager = new KalmanLocationManager(this);
        mPreferences = getPreferences(Context.MODE_PRIVATE);

        // Init maps
        int result = MapsInitializer.initialize(this);

        if (result != ConnectionResult.SUCCESS) {

            GooglePlayServicesUtil.getErrorDialog(result, this, 0).show();
            return;
        }

        // UI elements


        mMapView = this.findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mGoogleMap = googleMap;
                CircleOptions gpsCircleOptions = new CircleOptions()
                        .center(new LatLng(0.0, 0.0))
                        .radius(1.0)
                        .fillColor(getResources().getColor(R.color.activity_main_fill_gps))
                        .strokeColor(getResources().getColor(R.color.activity_main_stroke_gps))
                        .strokeWidth(1.0f)
                        .visible(false);

                mGpsCircle = mGoogleMap.addCircle(gpsCircleOptions);

                CircleOptions netCircleOptions = new CircleOptions()
                        .center(new LatLng(0.0, 0.0))
                        .radius(1.0)
                        .fillColor(getResources().getColor(R.color.activity_main_fill_net))
                        .strokeColor(getResources().getColor(R.color.activity_main_stroke_net))
                        .strokeWidth(1.0f)
                        .visible(false);

                mNetCircle = mGoogleMap.addCircle(netCircleOptions);

                mGoogleMap.getUiSettings().setZoomControlsEnabled(false);
                mGoogleMap.getUiSettings().setCompassEnabled(true);
//
                mGoogleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
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
        tvGps = (TextView) findViewById(R.id.tvGps);
        tvNet = (TextView) findViewById(R.id.tvNet);
        tvKal = (TextView) findViewById(R.id.tvKal);
        tvAlt = (TextView) findViewById(R.id.tvAlt);
        sbZoom = (SeekBar) findViewById(R.id.sbZoom);

        // Initial zoom level
//        sbZoom.setProgress(mPreferences.getInt("zoom", 80));

        // Map settings

        // Map elements

        // TextView animation
//        final float fromAlpha = 1.0f, toAlpha = 0.5f;
//
//        mGpsAnimation = new AlphaAnimation(fromAlpha, toAlpha);
//        mGpsAnimation.setDuration(GPS_TIME / 2);
//        mGpsAnimation.setFillAfter(true);
//        tvGps.startAnimation(mGpsAnimation);
//
//        mNetAnimation = new AlphaAnimation(fromAlpha, toAlpha);
//        mNetAnimation.setDuration(NET_TIME / 2);
//        mNetAnimation.setFillAfter(true);
//        tvNet.startAnimation(mNetAnimation);
//
//        mKalAnimation = new AlphaAnimation(fromAlpha, toAlpha);
//        mKalAnimation.setDuration(FILTER_TIME / 2);
//        mKalAnimation.setFillAfter(true);
//        tvKal.startAnimation(mKalAnimation);
//
//        // Init altitude textview
//        tvAlt.setText(getString(R.string.activity_main_fmt_alt, "-"));
    }


    @SuppressLint("MissingPermission")
    public void requestLocationUpdates(View view) {
        if(mKalmanLocationManager!=null && mLocationListener !=null)
            mKalmanLocationManager.requestLocationUpdates(
                    KalmanLocationManager.UseProvider.GPS_AND_NET, FILTER_TIME, GPS_TIME, NET_TIME, mLocationListener, true);

    }

    public void removeLocationUpdates(View view) {
        if(mKalmanLocationManager!=null && mLocationListener !=null)
                mKalmanLocationManager.removeUpdates(mLocationListener);

    }


    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();

        // Request location updates with the highest possible frequency on gps.
        // Typically, this means one update per second for gps.

        // For the network provider, which gives locations with less accuracy (less reliable),
        // request updates every 5 seconds.

        // For the filtertime argument we use a "real" value: the predictions are triggered by a timer.
        // Lets say we want 5 updates per second = update each 200 millis.


    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();

        // Remove location updates
//        mKalmanLocationManager.removeUpdates(mLocationListener);

        // Store zoom level
//        mPreferences.edit().putInt("zoom", sbZoom.getProgress()).apply();
    }

    /**
     * Listener used to get updates from KalmanLocationManager (the good old Android LocationListener).
     */
    private LocationListener mLocationListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

            String statusString = "Unknown";

            switch (status) {

                case LocationProvider.OUT_OF_SERVICE:
                    statusString = "Out of service";
                    break;

                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    statusString = "Temporary unavailable";
                    break;

                case LocationProvider.AVAILABLE:
                    statusString = "Available";
                    break;
            }

            Toast.makeText(
                    MainActivity.this,
                    String.format("Provider '%s' status: %s", provider, statusString),
                    Toast.LENGTH_SHORT)
            .show();
        }

        @Override
        public void onProviderEnabled(String provider) {

            Toast.makeText(
                    MainActivity.this, String.format("Provider '%s' enabled", provider), Toast.LENGTH_SHORT).show();

            // Remove strike-thru in label
            if (provider.equals(LocationManager.GPS_PROVIDER)) {

//                tvGps.setPaintFlags(tvGps.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
//                tvGps.invalidate();
            }

            if (provider.equals(LocationManager.NETWORK_PROVIDER)) {

//                tvNet.setPaintFlags(tvNet.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
//                tvNet.invalidate();
            }
        }

        @Override
        public void onProviderDisabled(String provider) {

//            Toast.makeText(
//                    MainActivity.this, String.format("Provider '%s' disabled", provider), Toast.LENGTH_SHORT).show();
//
//            // Set strike-thru in label and hide accuracy circle
//            if (provider.equals(LocationManager.GPS_PROVIDER)) {
//
//                tvGps.setPaintFlags(tvGps.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
//                tvGps.invalidate();
//                mGpsCircle.setVisible(false);
//            }
//
//            if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
//
//                tvNet.setPaintFlags(tvNet.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
//                tvNet.invalidate();
//                mNetCircle.setVisible(false);
//            }
        }
    };

    /**
     * Location Source for google maps 'my location' layer.
     */
    private LocationSource mLocationSource = new LocationSource() {

        @Override
        public void activate(OnLocationChangedListener onLocationChangedListener) {

            mOnLocationChangedListener = onLocationChangedListener;
        }

        @Override
        public void deactivate() {

            mOnLocationChangedListener = null;
        }
    };
}
