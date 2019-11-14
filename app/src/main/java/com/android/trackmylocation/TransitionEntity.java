package com.android.trackmylocation;

import java.io.Serializable;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class TransitionEntity  implements Serializable {
    public int getSlNo() {
        return slNo;
    }

    public void setSlNo(int slNo) {
        this.slNo = slNo;
    }

    @PrimaryKey(autoGenerate = true)
    private int slNo;
    String activity_type;
    String transition_type;

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

    public String getActivity_type() {
        return activity_type;
    }

    public void setActivity_type(String activity_type) {
        this.activity_type = activity_type;
    }

    public String getTransition_type() {
        return transition_type;
    }

    public void setTransition_type(String transition_type) {
        this.transition_type = transition_type;
    }
}