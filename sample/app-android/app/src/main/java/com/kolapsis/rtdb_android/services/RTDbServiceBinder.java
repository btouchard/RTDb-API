package com.kolapsis.rtdb_android.services;

import android.os.Binder;

public class RTDbServiceBinder extends Binder {
    private IRTDbService service;

    public RTDbServiceBinder(IRTDbService service) {
        this.service = service;
    }

    public IRTDbService getService() {
        return service;
    }
}
