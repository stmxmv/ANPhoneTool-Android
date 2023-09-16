package com.an.anphonetool.core;

import android.net.nsd.NsdServiceInfo;

import javax.jmdns.ServiceEvent;

public interface ServiceDiscoveryCallback {
    void onServiceFound(NsdServiceInfo serviceInfo);
    void onServiceRemove(NsdServiceInfo serviceEvent);
}
