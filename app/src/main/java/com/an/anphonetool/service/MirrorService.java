package com.an.anphonetool.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.net.Inet4Address;

public class MirrorService extends Service {

    private static int MIRROT_PORT = 13133;

    public class LocalBinder extends Binder {
        // 声明一个方法，getService。（提供给客户端调用）
        MirrorService getService() {
            // 返回当前对象LocalService,这样我们就可在客户端端调用Service的公共方法了
            return MirrorService.this;
        }
    }

    private LocalBinder binder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i("AN", "MirrorService is invoke onUnbind");
        return super.onUnbind(intent);
    }

    public void startMirror(Inet4Address address) {

    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
