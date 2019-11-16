package com.android.trackmylocation;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocationDao {
    @Insert
    void insertDirectGrnOffline(LocationEntity locationEntity);


    @Query("SELECT * FROM LocationEntity WHERE date_time  like  :id  AND address = :filter ORDER BY slNo DESC")
    List<LocationEntity> getLocationData(String id,String filter);


    @Query("DELETE  FROM LocationEntity")
    int deleteAllLogs();

    @Query("SELECT * FROM LocationEntity ORDER BY slNo DESC LIMIT 1")
    LocationEntity getLatestData();

    @Update
    int update(LocationEntity selfAttendance);


    @Delete
    int delete(LocationEntity locationEntity);


}
