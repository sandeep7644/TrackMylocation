package com.android.trackmylocation;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.NOTIFICATION_SERVICE;

public class RecognitionUpdatesBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LUBroadcastReceiver";
    public static final String ACTION_PROCESS_UPDATES =
            "com.freakyjolly.demobackgroundlocation.action" + ".PROCESS_UPDATES";
    Context mContext;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    LocationCallback locationCallback;
    DateFormat DFormat
            = DateFormat.getDateTimeInstance(
            DateFormat.LONG, DateFormat.LONG,
            Locale.getDefault());
    public static final int IN_VEHICLE = 0;
    public static final int ON_BICYCLE = 1;
    public static final int ON_FOOT = 2;
    public static final int STILL = 3;
    public static final int UNKNOWN = 4;
    public static final int TILTING = 5;
    public static final int WALKING = 7;
    public static final int RUNNING = 8;
    SharedPreferences sharedPreferences;

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        if (intent != null) {
            if (ActivityTransitionResult.hasResult(intent)) {
                sharedPreferences = context.getSharedPreferences("ActivityDashboardSharedPreferences", MODE_PRIVATE);
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                if (result == null) {
                    return;
                }
                ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

                for (DetectedActivity activity : detectedActivities) {
                    if (activity.getConfidence() > 75) {
                        broadcastActivity(activity);
//                log.info("Detected activity: " + activity.getType() + ", " + activity.getConfidence());

                    }
                }
            }
        }
    }


    private void broadcastActivity(DetectedActivity activity) {
        handleUserActivity(activity.getType());

    }


    private void handleUserActivity(int type) {
        String label = "UNKNOWN";
        int interval = 10000;

        switch (type) {
            case DetectedActivity.IN_VEHICLE: {
                label = "VEHICLE";
                interval = 10000;
                break;
            }
            case DetectedActivity.ON_BICYCLE: {
                label = "BICYCLE";
                break;
            }
            case DetectedActivity.ON_FOOT: {
                label = "FOOT";
                interval = 30000;
                break;
            }
            case DetectedActivity.RUNNING: {
                label = "RUNNING";
                interval = 11000;

                break;
            }
            case DetectedActivity.STILL: {
                label = "STILL";
                interval = 10000000;
                break;
            }
            case DetectedActivity.TILTING: {
                label = "TILTING";
                break;
            }
            case DetectedActivity.WALKING: {
                label = "WALKING";
                interval = 12000;

                break;
            }
            case DetectedActivity.UNKNOWN: {
                label = "UNKNOWN";
                break;
            }
        }

//        Timber.i("Activity changed %s", label);

        if (!label.equalsIgnoreCase("FOOT") && !label.equalsIgnoreCase("TILTING") && !label.equalsIgnoreCase("UNKNOWN")) {

//            if (!getSharedPreferences().getString("last_detected_activity", "").equalsIgnoreCase(label)
//                    | TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - getSharedPreferences().getLong("last_detected_activity_date_time", 0)) >= 1) {
                TransitionEntity transitionEntity = new TransitionEntity();
                transitionEntity.setActivity_type(label);
                transitionEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
                transitionEntity.setMillisecondsatActivitychange(String.valueOf(System.currentTimeMillis()));
                sharedPreferences.edit().putString("activity_unique_id", transitionEntity.getMillisecondsatActivitychange()).commit();
                OfflineEntries.getAppDatabase(mContext).transitionDao().insertDirectGrnOffline(transitionEntity);
                Intent intent_transition_update = new Intent("Action_NEW_TRANSITION");
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent_transition_update);
//                getLocation(type);
                getSharedPreferences().edit().putString("last_detected_activity", label).commit();
                getSharedPreferences().edit().putLong("last_detected_activity_date_time", System.currentTimeMillis()).commit();
                System.out.println("activity changed: "+label);
//            }
        }


    }



    private SharedPreferences getSharedPreferences() {
        if (sharedPreferences == null)
            sharedPreferences = mContext.getSharedPreferences("ActivityDashboardSharedPreferences", MODE_PRIVATE);
        return sharedPreferences;
    }



    private FusedLocationProviderClient getLocationClient() {
        if (mFusedLocationClient == null)
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);

        return mFusedLocationClient;
    }

    public LocationCallback getLocationCallback() {
        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);


                }
            };
        }
        return locationCallback;
    }

    public String getAddress(Location location) {
        Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());

        // Address found using the Geocoder.
        List<Address> addresses = null;
        Address address = null;
        String addressFragments = "";
        try {
            addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1);
            address = addresses.get(0);
        } catch (IOException ioException) {
            System.out.println(ioException);
        } catch (IllegalArgumentException illegalArgumentException) {
        }

        if (addresses == null || addresses.size() == 0) {
            addressFragments = "NO ADDRESS FOUND";
        } else {
            for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                addressFragments = addressFragments + String.valueOf(address.getAddressLine(i));
            }
        }
        return addressFragments;
    }


    @SuppressLint("MissingPermission")
    private void getLocation(int activityType) {
        long runStartTimeInMillis = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);
        sharedPreferences.edit().putLong("runStartTimeInMillis", runStartTimeInMillis).commit();
        Intent intent = new Intent(mContext, LocationUpdatesBroadcastReceiver.class);
        intent.setAction(LocationUpdatesBroadcastReceiver.LOCATION_RECEIVER_ACTION);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(getIntervalAccodingtoActivity(activityType));
        mLocationRequest.setFastestInterval(getIntervalAccodingtoActivity(activityType));
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setMaxWaitTime(getIntervalAccodingtoActivity(activityType));
        getLocationClient().removeLocationUpdates(pendingIntent);

        if (activityType == 3) {
            Task<Location> task = getLocationClient().getLastLocation();
            Location location = task.getResult();
            if (location != null && location.getAccuracy() <= 30) {
                LocationEntity locationEntity = new LocationEntity();
                locationEntity.setLatitude(location.getLatitude());
                locationEntity.setLongitude(location.getLongitude());
                locationEntity.setAccuracy(location.getAccuracy());
                locationEntity.setAddress(getAddress(location));
                locationEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
                locationEntity.setMillisecondsatActivitychange(sharedPreferences.getString("activity_unique_id", ""));
                OfflineEntries.getAppDatabase(mContext).locationDao().update(locationEntity);
                Intent location_found_intent = new Intent("Action_FOUND_LOCATION");
                location_found_intent.putExtra("latitude", location.getLatitude());
                location_found_intent.putExtra("longitude", location.getLongitude());
                location_found_intent.putExtra("accuracy", location.getAccuracy());
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(location_found_intent);
                Log.d(TAG, location.getLatitude() + "," + location.getLongitude() + ":" + location.getAccuracy());
            }


        } else {
            getLocationClient().requestLocationUpdates(mLocationRequest, pendingIntent);
        }

    }

    private String getTransitionTypeString(int transition) {
        switch (transition) {
            case 1: {
                return "EXIT";
            }

            case 0: {
                return "ENTER";
            }
        }
        return "UNKNOWN";
    }


    private String getActivityTypeString(int activityType) {
        switch (activityType) {
            case IN_VEHICLE: {
                return "VEHICLE";
            }

            case STILL: {
                return "STILL";
            }

            case WALKING: {
                return "WALKING";
            }

            case ON_FOOT: {
                return "STANDING";
            }

            case RUNNING: {
                return "RUNNING";
            }
        }
        return "UNKNOWN";
    }


    private int getIntervalAccodingtoActivity(int activityType) {
        switch (activityType) {
            case IN_VEHICLE: {
                return 2000;
            }

            case STILL: {
                return 1000000;
            }

            case WALKING: {
                return 10000;
            }

            case ON_FOOT: {
                return 20000;
            }

            case RUNNING: {
                return 7000;
            }
        }
        return 100000000;
    }


    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    public void showNotificationOngoing(Context context, String title) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        String address = "";
        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setContentText(address)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(new Notification.BigTextStyle().bigText(address))
                .setAutoCancel(true);
        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());

    }
}
