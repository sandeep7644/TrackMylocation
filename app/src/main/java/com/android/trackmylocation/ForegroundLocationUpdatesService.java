package com.android.trackmylocation;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;


import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

import static com.google.android.gms.location.DetectedActivity.IN_VEHICLE;
import static com.google.android.gms.location.DetectedActivity.ON_FOOT;
import static com.google.android.gms.location.DetectedActivity.RUNNING;
import static com.google.android.gms.location.DetectedActivity.STILL;
import static com.google.android.gms.location.DetectedActivity.WALKING;

/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 * <p>
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 * <p>
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification assocaited with that service is removed.
 */
public class ForegroundLocationUpdatesService extends Service {

    private static final String PACKAGE_NAME =
            "com.google.android.gms.location.sample.locationupdatesforegroundservice";

    private static final String TAG = ForegroundLocationUpdatesService.class.getSimpleName();
    float currentSpeed = 0.0f; // meters/second
    DateFormat DFormat
            = DateFormat.getDateTimeInstance(
            DateFormat.LONG, DateFormat.LONG,
            Locale.getDefault());
    /**
     * The name of the channel for notifications.
     */
    private static final String CHANNEL_ID = "channel_01";

    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";

    static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME +
            ".started_from_notification";

    private final IBinder mBinder = new LocalBinder();

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value.
     */
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    /**
     * The identifier for the notification displayed for the foreground service.
     */
    private static final int NOTIFICATION_ID = 12345678;

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private boolean mChangingConfiguration = false;

    private NotificationManager mNotificationManager;

    /**
     * Contains parameters used by {@link com.google.android.gms.location.FusedLocationProviderApi}.
     */
    private LocationRequest mLocationRequest;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * Callback for changes in location.
     */
    private LocationCallback mLocationCallback;

    private Handler mServiceHandler;

    /**
     * The current location.
     */
    ArrayList<Location> locations;
    int current_array_position = 0;
    private Location mLocation;
    private long runStartTimeInMillis;
    private KalmanLatLong kalmanFilter;
    private SharedPreferences sharedPreferences;
    private MedianFilter medialFilter;

    public ForegroundLocationUpdatesService() {
    }

    @Override
    public void onCreate() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };
        locations = new ArrayList<>();
        sharedPreferences = getSharedPreferences("ActivityDashboardSharedPreferences", MODE_PRIVATE);
        getLastLocation();
        medialFilter = new MedianFilter();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    private void broadcastActivity(DetectedActivity activity) {
        handleUserActivity(activity.getType());

    }


    private void handleUserActivity(int type) {
        String label = "UNKNOWN";
        int interval = 10000;

        switch (type) {
            case IN_VEHICLE: {
                label = "VEHICLE";
                interval = 1000;
                break;
            }
            case DetectedActivity.ON_BICYCLE: {
                label = "BICYCLE";
                break;
            }
            case ON_FOOT: {
                label = "FOOT";
                interval = 30000;
                break;
            }
            case RUNNING: {
                label = "RUNNING";
                interval = 2000;

                break;
            }
            case STILL: {
                label = "STILL";
                interval = 2000;
                break;
            }
            case DetectedActivity.TILTING: {
                label = "TILTING";
                break;
            }
            case WALKING: {
                label = "WALKING";
                interval = 3000;

                break;
            }
            case DetectedActivity.UNKNOWN: {
                label = "UNKNOWN";
                break;
            }
        }

        Timber.i("Activity changed %s", label);


        if (!label.equalsIgnoreCase("FOOT") && !label.equalsIgnoreCase("TILTING") && !label.equalsIgnoreCase("UNKNOWN")) {
            if (!sharedPreferences.getString("last_detected_activity", "").equalsIgnoreCase(label)
                    | TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - sharedPreferences.getLong("last_detected_activity_date_time", 0)) >= 1) {
                sharedPreferences.edit().putString("last_detected_activity", label).commit();
                sharedPreferences.edit().putLong("last_detected_activity_date_time", System.currentTimeMillis()).commit();
                sharedPreferences.edit().putString("activity_unique_id", label + "_" + System.currentTimeMillis()).commit();
                sharedPreferences.edit().putInt("location_interval", interval).commit();

                if (locations == null) {
                    locations = new ArrayList<>();
                } else {
                    locations.clear();
                }
                current_array_position = 0;
                TransitionEntity transitionEntity = new TransitionEntity();
                transitionEntity.setActivity_type(label);
                transitionEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
                transitionEntity.setMillisecondsatActivitychange(sharedPreferences.getString("activity_unique_id", "-1"));
                OfflineEntries.getAppDatabase(getApplicationContext()).transitionDao().insertDirectGrnOffline(transitionEntity);
                Intent intent_transition_update = new Intent("Action_NEW_TRANSITION");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent_transition_update);

                if (mFusedLocationClient != null && mLocationCallback != null) {
                    final Task<Void> voidTask = mFusedLocationClient.removeLocationUpdates(mLocationCallback);
                    voidTask.addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.e(TAG, "addOnCompleteListener: " + task.isComplete());
                            requestLocationUpdates();

                        }
                    });

                    final int finalInterval = interval;
                    voidTask.addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.e(TAG, "addOnSuccessListener: ");
                            UPDATE_INTERVAL_IN_MILLISECONDS = finalInterval;
                        }
                    });

                    voidTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "addOnFailureListener: ");
                            Log.e(TAG, e.getMessage());
                        }
                    });
                }


                Timber.i("Activity changed and eligible %s", label);

            }

        }


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

        // Get the list of the probable activities associated with the current state of the
        // device. Each activity is associated with a confidence level, which is an int between
        // 0 and 100.
        if (result == null) {
            return START_STICKY;
        }
        ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

        for (DetectedActivity activity : detectedActivities) {
            if (activity.getConfidence() > 75) {
                broadcastActivity(activity);
//                log.info("Detected activity: " + activity.getType() + ", " + activity.getConfidence());

            }
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration) {
            Log.i(TAG, "Starting foreground service");

            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     */
    public void requestLocationUpdates() {
        createLocationRequest();
        runStartTimeInMillis = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);
        Log.i(TAG, "Requesting location updates");
        Utils.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), ForegroundLocationUpdatesService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Utils.setRequestingLocationUpdates(this, false);
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     */
    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            Utils.setRequestingLocationUpdates(this, false);
//            stopSelf();
        } catch (SecurityException unlikely) {
            Utils.setRequestingLocationUpdates(this, true);
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }

    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
    private Notification getNotification() {
        Intent intent = new Intent(this, ForegroundLocationUpdatesService.class);

        CharSequence text = "Location track";

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

//         The PendingIntent to launch activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_bike_enter, getString(R.string.activity_main_action_settings),
                        activityPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.remove_location_updates),
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID); // Channel ID
        }

        return builder.build();
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                            } else {
                                Log.w(TAG, "Failed to get location.");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

    @TargetApi(26)
    private void createChannel(NotificationManager notificationManager) {
        String name = "FileDownload";
        String description = "Notifications for download status";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel mChannel = new NotificationChannel(name, name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.BLUE);
        notificationManager.createNotificationChannel(mChannel);
    }

    @SuppressLint("NewApi")
    private long getLocationAge(Location newLocation) {
        long locationAge;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            long currentTimeInMilli = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);
            long locationTimeInMilli = (long) (newLocation.getElapsedRealtimeNanos() / 1000000);
            locationAge = currentTimeInMilli - locationTimeInMilli;
        } else {
            locationAge = System.currentTimeMillis() - newLocation.getTime();
        }
        return locationAge;
    }

    private void onNewLocation(Location location) {
        long age = getLocationAge(location);

        if (age > 5 * 1000) { //more than 5 seconds
            Log.d(TAG, "Location is old");
            return;
        }

        if (location.getAccuracy() <= 0) {
            Log.d(TAG, "Latitidue and longitude values are invalid.");
            return;
        }

        //setAccuracy(newLocation.getAccuracy());
        float horizontalAccuracy = location.getAccuracy();
        if (horizontalAccuracy > 30) { //30meter filter
            Log.d(TAG, "Accuracy is too low.");
            return;
        }


        LocationEntity normallocationEntity = new LocationEntity();
        normallocationEntity.setLatitude(location.getLatitude());
        normallocationEntity.setLongitude(location.getLongitude());
        normallocationEntity.setAccuracy(location.getAccuracy());
        normallocationEntity.setAddress("normallocation");
        normallocationEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
        normallocationEntity.setMillisecondsatActivitychange(sharedPreferences.getString("activity_unique_id", "0"));
        OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().insertDirectGrnOffline(normallocationEntity);

        /* Kalman Filter */
        float Qvalue;

        long locationTimeInMillis = (long) (location.getElapsedRealtimeNanos() / 1000000);
        long elapsedTimeInMillis = locationTimeInMillis - runStartTimeInMillis;

        if (currentSpeed == 0.0f) {
            Qvalue = 3.0f; //3 meters per second
        } else {
            Qvalue = currentSpeed; // meters per second
        }

        getKalmanFilter().Process(location.getLatitude(), location.getLongitude(), location.getAccuracy(), elapsedTimeInMillis, Qvalue);
        double predictedLat = getKalmanFilter().get_lat();
        double predictedLng = getKalmanFilter().get_lng();

        Location predictedLocation = new Location("");//provider name is unecessary
        predictedLocation.setLatitude(predictedLat);//your coords of course
        predictedLocation.setLongitude(predictedLng);
        float predictedDeltaInMeters = predictedLocation.distanceTo(location);

        if (predictedDeltaInMeters > 40) {
            Log.d(TAG, "Kalman Filter detects mal GPS, we should probably remove this from track");
            kalmanFilter.consecutiveRejectCount += 1;

            if (kalmanFilter.consecutiveRejectCount > 3) {
                kalmanFilter = new KalmanLatLong(3); //reset Kalman Filter if it rejects more than 3 times in raw.
            }

            return;
        } else {
            kalmanFilter.consecutiveRejectCount = 0;
        }

        LocationEntity locationEntityKalnan = new LocationEntity();
        locationEntityKalnan.setLatitude(predictedLocation.getLatitude());
        locationEntityKalnan.setLongitude(predictedLocation.getLongitude());
        locationEntityKalnan.setAccuracy(predictedLocation.getAccuracy());
        locationEntityKalnan.setAddress("predicted_filter");
        locationEntityKalnan.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
        locationEntityKalnan.setMillisecondsatActivitychange(sharedPreferences.getString("activity_unique_id", "0"));
        OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().insertDirectGrnOffline(locationEntityKalnan);


        Log.d(TAG, "Location quality is good enough.");
        LocationEntity locationEntity = new LocationEntity();
        locationEntity.setLatitude(location.getLatitude());
        locationEntity.setLongitude(location.getLongitude());
        locationEntity.setAccuracy(location.getAccuracy());
        locationEntity.setAddress("without_filter");
        locationEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
        locationEntity.setMillisecondsatActivitychange(sharedPreferences.getString("activity_unique_id", "0"));
        OfflineEntries.getAppDatabase(getApplicationContext()).locationDao().insertDirectGrnOffline(locationEntity);


        Intent location_found_intent = new Intent("Action_FOUND_LOCATION");
        location_found_intent.putExtra("latitude", location.getLatitude());
        location_found_intent.putExtra("longitude", location.getLongitude());
        location_found_intent.putExtra("accuracy", location.getAccuracy());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(location_found_intent);
        Log.d(TAG, location.getLatitude() + "," + location.getLongitude() + ":" + location.getAccuracy() + ":" + mLocationRequest.getInterval());

        if (sharedPreferences.getString("last_detected_activity", "").equalsIgnoreCase("STILL")) {
            removeLocationUpdates();
        }

        currentSpeed = predictedLocation.getSpeed();
    }

    private KalmanLatLong getKalmanFilter() {
        if (kalmanFilter == null) {
            kalmanFilter = new KalmanLatLong(3);

        }
        return kalmanFilter;
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        ForegroundLocationUpdatesService getService() {
            return ForegroundLocationUpdatesService.this;
        }
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The {@link Context}.
     */
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }
}