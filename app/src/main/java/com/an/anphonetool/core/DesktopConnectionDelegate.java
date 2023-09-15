package com.an.anphonetool.core;


public interface DesktopConnectionDelegate {
    void onConnect(boolean success);
    void onError(Exception e);

    void onFileSendProgress(double progress, double byteRate);
    void onFileSendComplete();

    void toast(String msg);

    void onRing();
}
