package com.android.trackmylocation;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface TransitionDao {
    @Insert
    void insertDirectGrnOffline(TransitionEntity directGRNOfflineEntry);



    @Query("SELECT * FROM TransitionEntity ORDER BY slNo DESC")
    List<TransitionEntity> getAllOfflineGrnList();

 @Query("DELETE  FROM TransitionEntity")
  int  deleteAllLogs();


    @Delete
    int delete(TransitionEntity directGRNOfflineEntry);






}
