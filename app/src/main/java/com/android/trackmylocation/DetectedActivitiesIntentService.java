package com.android.trackmylocation;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Response;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import timber.log.Timber;

import static com.google.android.gms.location.DetectedActivity.IN_VEHICLE;
import static com.google.android.gms.location.DetectedActivity.ON_FOOT;
import static com.google.android.gms.location.DetectedActivity.RUNNING;
import static com.google.android.gms.location.DetectedActivity.STILL;
import static com.google.android.gms.location.DetectedActivity.WALKING;


public class DetectedActivitiesIntentService extends Service {

    protected static final String TAG = DetectedActivitiesIntentService.class.getSimpleName();
    private SharedPreferences sharedPreferences;
    private ConnectivityManager connection_manager;
    private HashMap<String, String> params;
    private SimpleDateFormat simpleDateFormat;
    private IntentFilter ifilter;
    private Intent batteryStatus;
    private OfflineEntries offlineEntries;
    //    private Logger log;
    DateFormat DFormat
            = DateFormat.getDateTimeInstance(
            DateFormat.LONG, DateFormat.LONG,
            Locale.getDefault());
    private FusedLocationProviderClient mFusedLocationClient;

    public DetectedActivitiesIntentService() {
        // Use the TAG to name the worker thread.
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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



    private void broadcastActivity(DetectedActivity activity) {
        handleUserActivity(activity.getType());

    }


    private void handleUserActivity(int type) {
        String label = "UNKNOWN";
        int interval = 10000;

        switch (type) {
            case IN_VEHICLE: {
                label = "VEHICLE";
                interval = 5000;
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
                interval = 11000;

                break;
            }
            case STILL: {
                label = "STILL";
                interval = 60000;
                break;
            }
            case DetectedActivity.TILTING: {
                label = "TILTING";
                break;
            }
            case WALKING: {
                label = "WALKING";
                interval = 12000;

                break;
            }
            case DetectedActivity.UNKNOWN: {
                label = "UNKNOWN";
                break;
            }
        }

        Timber.i("Activity changed %s", label);


        if (!label.equalsIgnoreCase("FOOT") && !label.equalsIgnoreCase("TILTING") && !label.equalsIgnoreCase("UNKNOWN")) {
            if (!getSharedPreferences().getString("last_detected_activity", "").equalsIgnoreCase(label)
                    | TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - getSharedPreferences().getLong("last_detected_activity_date_time", 0)) >= 1) {
                getSharedPreferences().edit().putString("last_detected_activity", label).commit();
                getSharedPreferences().edit().putLong("last_detected_activity_date_time", System.currentTimeMillis()).commit();
                getSharedPreferences().edit().putString("activity_unique_id", label+"_"+System.currentTimeMillis()).commit();
                getSharedPreferences().edit().putInt("location_interval", interval).commit();

                TransitionEntity transitionEntity = new TransitionEntity();
                transitionEntity.setActivity_type(label);
                transitionEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
                transitionEntity.setMillisecondsatActivitychange(getSharedPreferences().getString("activity_unique_id","-1"));
                OfflineEntries.getAppDatabase(getApplicationContext()).transitionDao().insertDirectGrnOffline(transitionEntity);
                Intent intent_transition_update = new Intent("Action_NEW_TRANSITION");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent_transition_update);

                getLocation(interval,label);

                Timber.i("Activity changed and eligible %s", label);

            }

        }


    }

    @SuppressLint("MissingPermission")
    private void getLocation(int interval,String activity_label) {

        long runStartTimeInMillis = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);
        sharedPreferences.edit().putLong("runStartTimeInMillis", runStartTimeInMillis).commit();
        Intent intent = new Intent(getApplicationContext(), LocationUpdatesBroadcastReceiver.class);
        intent.setAction(LocationUpdatesBroadcastReceiver.LOCATION_RECEIVER_ACTION);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(interval);
        mLocationRequest.setFastestInterval(interval);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setMaxWaitTime(interval);
        getLocationClient().removeLocationUpdates(pendingIntent);

//        if (activityType == 3) {
//            mLocationRequest.setNumUpdates(1);
//        }

        getLocationClient().requestLocationUpdates(mLocationRequest, pendingIntent);


    }


    private FusedLocationProviderClient getLocationClient() {
        if (mFusedLocationClient == null)
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        return mFusedLocationClient;
    }


    private SimpleDateFormat getSimpleDateFormat() {
        if (simpleDateFormat == null)
            simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault());

        return simpleDateFormat;
    }


    private OfflineEntries getOfflineDatabase() {
        if (offlineEntries == null)
            offlineEntries = OfflineEntries.getAppDatabase(this);

        return offlineEntries;
    }


    SimpleDateFormat getDateFormat() {

        return new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault());
    }


    private SharedPreferences getSharedPreferences() {
        if (sharedPreferences == null)
            sharedPreferences = getSharedPreferences("ActivityDashboardSharedPreferences", MODE_PRIVATE);
        return sharedPreferences;
    }

}
