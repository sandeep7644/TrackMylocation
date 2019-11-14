package com.android.trackmylocation;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity
public class LocationEntity implements Serializable {
    public int getSlNo() {
        return slNo;
    }

    public void setSlNo(int slNo) {
        this.slNo = slNo;
    }

    @PrimaryKey(autoGenerate = true)
    private int slNo;

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    double latitude;
    double longitude;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    String address;

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    float accuracy;

    public String getMillisecondsatActivitychange() {
        return millisecondsatActivitychange;
    }

    public void setMillisecondsatActivitychange(String millisecondsatActivitychange) {
        this.millisecondsatActivitychange = millisecondsatActivitychange;
    }

    String millisecondsatActivitychange;

    public String getDate_time() {
        return date_time;
    }

    public void setDate_time(String date_time) {
        this.date_time = date_time;
    }

    String date_time;


}