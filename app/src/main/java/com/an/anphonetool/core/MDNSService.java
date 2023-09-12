package com.an.anphonetool.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class MDNSService implements ServiceListener {
    private static String RegType = "_anphonetool._tcp.local.";
    private JmDNS jmdns;
    private ServiceDiscoveryCallback _discoveryCallback;

    private JmDNS getJmDNS() throws IOException {
        if (jmdns == null) {
            jmdns = JmDNS.create("ANPhoneTool");
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
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        System.out.println("Service resolved: " + event.getInfo());
        _discoveryCallback.onServiceFound(event);
    }
}
