package com.android.trackmylocation;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {TransitionEntity.class,LocationEntity.class}, version = 2,exportSchema =false)
public abstract  class OfflineEntries  extends RoomDatabase {
    public abstract TransitionDao transitionDao();
    public abstract LocationDao locationDao();

    private static OfflineEntries offlineEntries;
    public static synchronized  OfflineEntries getAppDatabase(Context context) {
        if (offlineEntries == null) {
            offlineEntries = Room.databaseBuilder
                    (context, OfflineEntries.class, "OFFLINE_DATABASE")
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return offlineEntries;
    }


}