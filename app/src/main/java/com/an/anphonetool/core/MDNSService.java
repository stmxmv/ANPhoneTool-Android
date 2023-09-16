package com.an.anphonetool.core;

import static android.content.Context.WIFI_SERVICE;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Enumeration;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public class MDNSService {

    private NsdManager nsdManager;
    private ServiceDiscoveryCallback discoveryCallback;

    private final String serviceType = "_anphonetool._tcp.";

    private NsdManager.DiscoveryListener discoveryListener;

    public MDNSService(Context context) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void startDiscovery(ServiceDiscoveryCallback callback) {
        discoveryCallback = callback;

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("MDNSService", "Discovery failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("MDNSService", "Stop discovery failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d("MDNSService", "Discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d("MDNSService", "Discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d("MDNSService", "Service found: " + serviceInfo);
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {

                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
                        discoveryCallback.onServiceFound(nsdServiceInfo);
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d("MDNSService", "Service lost: " + serviceInfo);
                discoveryCallback.onServiceRemove(serviceInfo);
            }
        };

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        if (nsdManager != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
        }
    }

//    public interface ServiceDiscoveryCallback {
//        void onServiceFound(NsdServiceInfo serviceInfo);
//        void onServiceLost(NsdServiceInfo serviceInfo);
//    }
}