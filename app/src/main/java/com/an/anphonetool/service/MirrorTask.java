package com.an.anphonetool.service;

import com.an.anphonetool.Ln;
import com.an.anphonetool.mirror.CodecOption;
import com.an.anphonetool.mirror.Device;
import com.an.anphonetool.mirror.Options;
import com.an.anphonetool.mirror.ScreenEncoder;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

public class MirrorTask implements Runnable {

    private FileDescriptor fd;

    public MirrorTask(FileDescriptor fd) {
        this.fd = fd;
    }

    @Override
    public void run() {
        Options options = new Options();
        options.setMaxSize(720);
        options.setBitRate(8000000);
        options.setMaxFps(0);

        options.setSendFrameMeta(false);
        options.setSendDeviceMeta(false);
        options.setSendDummyByte(false);

        /// TODO set options

        Device device = new Device(options);

        List<CodecOption> codecOptions = options.getCodecOptions();

        ScreenEncoder screenEncoder =
                new ScreenEncoder(
                        options.getSendFrameMeta(),
                        options.getBitRate(),
                        options.getMaxFps(),
                        codecOptions,
                        options.getEncoderName(),
                        options.getDownsizeOnError());


        try {
            // synchronous
            screenEncoder.streamScreen(device, fd);
        } catch (IOException e) {
            // this is expected on close
            Ln.d("Screen streaming stopped");
        } finally {

        }
    }
}
