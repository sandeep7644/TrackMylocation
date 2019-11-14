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
import android.view.View;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import timber.log.Timber;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.NOTIFICATION_SERVICE;

public class LocationUpdatesBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LUBroadcastReceiver";
    public static final String LOCATION_RECEIVER_ACTION =
            "com.freakyjolly.demobackgroundlocation.action" + ".PROCESS_LOCATION_UPDATES";
    Context mContext;
    DateFormat DFormat
            = DateFormat.getDateTimeInstance(
            DateFormat.LONG, DateFormat.LONG,
            Locale.getDefault());
    private SharedPreferences sharedPreferences;
    private long runStartTimeInMillis;
    float currentSpeed = 0.0f; // meters/second
    KalmanLatLong kalmanFilter;
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private FusedLocationProviderClient mFusedLocationClient;

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onReceive(final Context context, Intent intent) {
        mContext = context;
        System.out.println(TAG + intent);
        if (intent != null) {
            final String action = intent.getAction();
            if (LOCATION_RECEIVER_ACTION.equals(action)) {
                LocationResult result = LocationResult.extractResult(intent);
                if (result == null) {
                    return;
                }


                final Location firstLocation = result.getLastLocation();
                sharedPreferences = context.getSharedPreferences("ActivityDashboardSharedPreferences", MODE_PRIVATE);
                runStartTimeInMillis = sharedPreferences.getLong("runStartTimeInMillis", 0);


                if (firstLocation != null) {

                    filterAndAddLocation(firstLocation, context);

                }

            }
        }
    }


    private FusedLocationProviderClient getLocationClient() {
        if (mFusedLocationClient == null)
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);

        return mFusedLocationClient;
    }

    public void removeLocationUpdates() {
        Timber.d("locations removed because user stopped");
        Intent intent = new Intent(mContext, LocationUpdatesBroadcastReceiver.class);
        intent.setAction(LocationUpdatesBroadcastReceiver.LOCATION_RECEIVER_ACTION);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        getLocationClient().removeLocationUpdates(pendingIntent);
    }

    private KalmanLatLong getKalmanFilter() {
        if (kalmanFilter == null) {
            kalmanFilter = new KalmanLatLong(3);

        }
        return kalmanFilter;
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


    private void filterAndAddLocation(Location location, Context context) {

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
            getKalmanFilter().consecutiveRejectCount += 1;

            if (getKalmanFilter().consecutiveRejectCount > 3) {
                kalmanFilter = new KalmanLatLong(3); //reset Kalman Filter if it rejects more than 3 times in raw.
            }

            return;
        } else {
            kalmanFilter.consecutiveRejectCount = 0;
        }

        Log.d(TAG, "Location quality is good enough.");
        LocationEntity locationEntity = new LocationEntity();
        locationEntity.setLatitude(location.getLatitude());
        locationEntity.setLongitude(location.getLongitude());
        locationEntity.setAccuracy(location.getAccuracy());
        locationEntity.setAddress("without_filter");
        locationEntity.setDate_time(DFormat.format(Calendar.getInstance().getTime()));
        locationEntity.setMillisecondsatActivitychange(sharedPreferences.getString("activity_unique_id", "0"));
        OfflineEntries.getAppDatabase(context).locationDao().insertDirectGrnOffline(locationEntity);


        Intent location_found_intent = new Intent("Action_FOUND_LOCATION");
        location_found_intent.putExtra("latitude", location.getLatitude());
        location_found_intent.putExtra("longitude", location.getLongitude());
        location_found_intent.putExtra("accuracy", location.getAccuracy());
        LocalBroadcastManager.getInstance(context).sendBroadcast(location_found_intent);
        Log.d(TAG, location.getLatitude() + "," + location.getLongitude() + ":" + location.getAccuracy());

//        if (sharedPreferences.getString("last_detected_activity", "").equalsIgnoreCase("STILL")) {
//            removeLocationUpdates();
//        }

        currentSpeed = predictedLocation.getSpeed();
    }


    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
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


    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    public void showNotificationOngoing(Context context, Location location) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        String address = getAddress(location);
        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setContentTitle("Location" + DateFormat.getDateTimeInstance().format(new Date()) + ":" + location.getAccuracy())
                .setContentText(address)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setStyle(new Notification.BigTextStyle().bigText(address))
                .setAutoCancel(true);
        notificationManager.notify(3, notificationBuilder.build());

    }
}
