package com.an.anphonetool.core;

import com.an.anphonetool.DeviceMessageOuterClass;

public interface DesktopControlHandlerDelegate {
    void onControlActive();
    void onControlInActive();

    void onControlReadMessage( DeviceMessageOuterClass.DeviceMessage message);
}
