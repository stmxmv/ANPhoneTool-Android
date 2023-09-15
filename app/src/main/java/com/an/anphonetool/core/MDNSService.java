package com.an.anphonetool.core;

import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class MDNSService implements ServiceListener {
    private static String RegType = "_anphonetool._tcp.local.";
    private JmDNS jmdns;
    private ServiceDiscoveryCallback _discoveryCallback;

    /// to avoid loopback or something
    public InetAddress getCurrentIp() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) networkInterfaces
                        .nextElement();
                Enumeration<InetAddress> nias = ni.getInetAddresses();
                while(nias.hasMoreElements()) {
                    InetAddress ia= (InetAddress) nias.nextElement();
                    if (!ia.isLinkLocalAddress()
                            && !ia.isLoopbackAddress()
                            && ia instanceof Inet4Address) {
                        return ia;
                    }
                }
            }
        } catch (SocketException e) {
            Log.d("AN", "unable to get current IP " + e.getMessage(), e);
        }
        return null;
    }

    private JmDNS getJmDNS() throws IOException {
        if (jmdns == null) {
            jmdns = JmDNS.create(getCurrentIp());
            Log.d("AN", "MDNS discovery at address " + jmdns.getInetAddress());
        }
        return jmdns;
    }

    public void startDiscovery(ServiceDiscoveryCallback callback) throws IOException {
        _discoveryCallback = callback;
        getJmDNS().addServiceListener(RegType, this);
    }

    public void stopDiscovery() {
        if (jmdns == null) return;
        jmdns.removeServiceListener(RegType, this);
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        System.out.println("Service added: " + event.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        System.out.println("Service removed: " + event.getInfo());
        _discoveryCallback.onServiceRemove(event);
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        System.out.println("Service resolved: " + event.getInfo());
        _discoveryCallback.onServiceFound(event);
    }
}
