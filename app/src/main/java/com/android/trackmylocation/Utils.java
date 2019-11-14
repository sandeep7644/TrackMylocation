package com.android.trackmylocation;

import android.content.Context;
import android.location.Location;
import android.preference.PreferenceManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    public static final String KEY_REQUESTING_LOCATION_UPDATES = "requesting_locaction_updates";
    private static SimpleDateFormat simpleDateFormat;


    static boolean requestingLocationUpdates(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false);
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    static void setRequestingLocationUpdates(Context context, boolean requestingLocationUpdates) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
                .apply();
    }

    /**
     * Returns the {@code location} object as a human readable string.
     * @param location  The {@link Location}.
     */
    public static String getLocationText(Location location,String activity) {
        if(BuildConfig.DEBUG)
        return location == null ? "Unknown location" :
                "(" + location.getLatitude() + ", " + location.getLongitude() + ")"+",Last Activity: "+activity+",Last updated at: "+ getSimpleDateFormat().format(new Date());

        return "Concord ERP is running";

    }

    static String getLocationTitle(Context context) {
        return context.getString(R.string.location_updated,
                DateFormat.getDateTimeInstance().format(new Date()));
    }


    public static SimpleDateFormat getSimpleDateFormat() {
        if (simpleDateFormat == null)
            simpleDateFormat = new SimpleDateFormat("dddd-MM-yyyy hh:mm:ss a", Locale.getDefault());

        return simpleDateFormat;
    }


}