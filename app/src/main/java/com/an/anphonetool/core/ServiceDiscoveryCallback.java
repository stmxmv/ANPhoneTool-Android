package com.an.anphonetool.core;

import javax.jmdns.ServiceEvent;

public interface ServiceDiscoveryCallback {
    void onServiceFound(ServiceEvent serviceInfo);
    void onServiceRemove(ServiceEvent serviceEvent);
}
