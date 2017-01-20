package com.kolapsis.rtdb_android.services;

public interface IRTDbService {
    void addListener(String table, RTDbService.Listener listener);
    void removeListener(String table, RTDbService.Listener listener);
}
