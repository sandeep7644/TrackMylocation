package com.android.trackmylocation;

import android.location.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MedianFilter {
    ArrayList<Double> latitude, longitude;

    public void MedianFilter() {
        latitude = new ArrayList<>();
        longitude = new ArrayList<>();
    }

    double getMedian(ArrayList<Double> arrayList) {
        Collections.sort(arrayList);
        if (arrayList.size() % 2 == 0)
            return (arrayList.get(arrayList.size() / 2) + arrayList.get(arrayList.size() / 2 - 1)) / 2;
        else
            return arrayList.get(arrayList.size() / 2);

    }

    public Location processLocation(List<Location> locations) {
        if (latitude == null) {
            latitude = new ArrayList<>();
        }

        if (longitude == null) {
            longitude = new ArrayList<>();
        }
        latitude.clear();
        longitude.clear();
        for (Location location : locations) {
            latitude.add(location.getLatitude());
            longitude.add(location.getLongitude());
        }

        double lat_median = getMedian(latitude);
        double lng_median = getMedian(longitude);

        Location location = new Location("MEDIAN");
        location.setLatitude(lat_median);
        location.setLongitude(lng_median);
        System.out.println("Median filter location:"+lat_median+","+lng_median);
        return location;
    }


}
