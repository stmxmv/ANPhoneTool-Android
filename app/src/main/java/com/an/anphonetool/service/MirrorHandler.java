package com.an.anphonetool.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.an.anphonetool.Ln;
//import com.an.anphonetool.mirror.CodecOption;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

public class MirrorHandler extends Service implements Runnable {

    private static final int DEFAULT_I_FRAME_INTERVAL = 1; // seconds
    private static final int REPEAT_FRAME_DELAY_US = 100_000; // repeat after 100ms
    private static final String KEY_MAX_FPS_TO_ENCODER = "max-fps-to-encoder";
    private int width;
    private int height;

    private MediaProjection mp;
    private MediaFormat mediaFormat;
    private MediaCodec mediaCodec;
    private boolean firstFrameSent;
    private Thread encodingThread;
    private SocketChannel output;

    private volatile boolean running = false;

    private MirrorHandlerDelegate delegate;

    public void setDelegate(MirrorHandlerDelegate delegate) {
        this.delegate = delegate;
    }

    private static void setCodecOption(MediaFormat format) {
//        String key = codecOption.getKey();
//        Object value = codecOption.getValue();

//        if (value instanceof Integer) {
//            format.setInteger(key, (Integer) value);
//        } else if (value instanceof Long) {
//            format.setLong(key, (Long) value);
//        } else if (value instanceof Float) {
//            format.setFloat(key, (Float) value);
//        } else if (value instanceof String) {
//            format.setString(key, (String) value);
//        }

//        Ln.d("Codec option set: " + key + " (" + value.getClass().getSimpleName() + ") = " + value);
    }

    private MediaFormat createFormat(int bitRate, int maxFps) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

//        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);

        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        // must be present to configure the encoder, but does not impact the actual frame rate, which is variable
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL);

        // display the very first frame, and recover from bad quality when no new frames
//        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US); // µs

        if (maxFps > 0) {
            // The key existed privately before Android 10:
            // <https://android.googlesource.com/platform/frameworks/base/+/625f0aad9f7a259b6881006ad8710adce57d1384%5E%21/>
            // <https://github.com/Genymobile/scrcpy/issues/488#issuecomment-567321437>
            format.setFloat(KEY_MAX_FPS_TO_ENCODER, maxFps);
        }

//        if (codecOptions != null) {
//            for (CodecOption option : codecOptions) {
//                setCodecOption(format, option);
//            }
//        }

        return format;
    }

    public class LocalBinder extends Binder {
        // 声明一个方法，getService。（提供给客户端调用）
        public MirrorHandler getService() {
            // 返回当前对象LocalService,这样我们就可在客户端端调用Service的公共方法了
            return MirrorHandler.this;
        }
    }

    private MirrorHandler.LocalBinder binder = new LocalBinder();

    public void prepare(MediaProjection mp, int width, int height) {
        this.width = width;
        this.height = height;
        this.mp = mp;
    }


    public void start(SocketChannel out) throws IOException {
        output = out;
        encodingThread = new Thread(this);
        encodingThread.start();
    }

    public void stop() {
        running = false;

        try {
            encodingThread.join(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        mediaFormat = createFormat(8000000, 0);

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = mediaCodec.createInputSurface();

        mp.createVirtualDisplay("ANPhoneMirror",
                width, height, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);

        mediaCodec.start();
        boolean eof = false;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        running = true;

        while (true) {
            try {
                if (output.finishConnect())
                    break;

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        while (!eof && running) {
            int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
            eof = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            try {
//                if (consumeRotationChange()) {
//                    // must restart encoding with new size
//                    break;
//                }
                if (outputBufferId >= 0) {
                    ByteBuffer codecBuffer = mediaCodec.getOutputBuffer(outputBufferId);

//                    if (sendFrameMeta) {
//                        writeFrameMeta(fd, bufferInfo, codecBuffer.remaining());
//                    }

                    while (codecBuffer.hasRemaining()) {
                        output.write(codecBuffer);
                    }

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        // If this is not a config packet, then it contains a frame
                        firstFrameSent = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
                stopSelf();

                if (delegate != null) {
                    delegate.onMirrorHandlerStop();
                }
                /// ??????
//                encodingThread.interrupt();
                break;
            } finally {
                if (outputBufferId >= 0) {
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }
        }
    }


    private void startForeground()
    {
        String NOTIFICATION_CHANNEL_ID = "com.anphonetool";
        String channelName = "Screen Mirror Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Screen is mirroring in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(1, notification);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

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
}
