package com.an.anphonetool.core;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.an.anphonetool.DesktopMessageOuterClass;
import com.an.anphonetool.DeviceMessageOuterClass;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.UUID;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;


public class DesktopConnection implements DesktopControlHandlerDelegate {

    private static int CONTROL_PORT = 13131;
    private static int DATA_PORT = 13132;

    private InetAddress address;
    private EventLoopGroup eventLoopGroup;

    private DesktopControlHandler desktopControlHandler;
    private DesktopDataHandler desktopDataHandler;

    private DesktopConnectionDelegate delegate;

    private HashMap<UUID, String> sendFileMap = new HashMap<>();

    public DesktopConnection(InetAddress address) {
        this.address = address;
        desktopControlHandler = new DesktopControlHandler();
        desktopDataHandler = new DesktopDataHandler();
        desktopControlHandler.setDelegate(this);
        desktopDataHandler.setControlHandler(desktopControlHandler);
    }

    public DesktopConnection() {
        desktopControlHandler = new DesktopControlHandler();
        desktopDataHandler = new DesktopDataHandler();
        desktopControlHandler.setDelegate(this);
        desktopDataHandler.setControlHandler(desktopControlHandler);
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public InetAddress getAddress() {
        return this.address;
    }

    public void start() {
        eventLoopGroup = new NioEventLoopGroup();
        desktopControlHandler
                .connect(address, eventLoopGroup)
                .addListener((@NonNull ChannelFuture future) -> {
                    delegate.onConnect(true);
        });
        desktopDataHandler
                .connect(address,  eventLoopGroup)
                .addListener((@NonNull ChannelFuture future) -> {
                    delegate.onConnect(true);
                });
    }

    public void stop() {
        eventLoopGroup.shutdownGracefully();
        desktopControlHandler.stop();;
        desktopDataHandler.stop();

        eventLoopGroup = null;
        desktopControlHandler = null;
        desktopDataHandler = null;
    }

    public void reset()  {
        desktopControlHandler = new DesktopControlHandler();
        desktopDataHandler = new DesktopDataHandler();
        desktopControlHandler.setDelegate(this);
        desktopDataHandler.setControlHandler(desktopControlHandler);
    }

    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }

    public DesktopConnectionDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(DesktopConnectionDelegate delegate) {
        this.delegate = delegate;
    }

    public void sendMessage(GeneratedMessageV3 message) {
        SocketChannel channel = desktopControlHandler.getChannel();
        if (channel != null) {
            channel.writeAndFlush(message);
        }
    }

    public void sendFile(Uri uri, Context context) {
        try {
            ContentResolver resolver = context.getContentResolver();
            AssetFileDescriptor fileDescriptor = resolver.openAssetFileDescriptor(uri, "r");
            if (fileDescriptor == null) return;

            long fileSize = fileDescriptor.getLength();

            String fileName = Utility.getFileNameFromUri(resolver, uri);

            InputStream inputStream = resolver.openInputStream(uri);

            assert(inputStream != null);

            if (fileName == null) {
                DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
                fileName = documentFile.getName();
//                    fileName = documentFile.getUri().getPath();
                if (fileName == null) {
                    fileName = UUID.randomUUID().toString();
                }
            }

            UUID fileUUID = UUID.randomUUID();

            FileUtils fileUtils = new FileUtils(context);
            String path = fileUtils.getPath(uri);

            File file = new File(path);

            if (!file.exists()) {

                File copyFile = new File(context.getFilesDir().getPath() + File.separatorChar + fileName);
                try (OutputStream os = Files.newOutputStream(copyFile.toPath())) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                    os.flush();
                } catch (Exception ex) {
                    delegate.onError(ex);
                }
                path = copyFile.getPath();
            }

            sendFileMap.put(fileUUID, path);
            Log.d("AN", "will send file with uuid " + fileUUID);

            DesktopMessageOuterClass.SendFileInfo sendFileInfo =
                    DesktopMessageOuterClass.SendFileInfo.newBuilder()
                            .setUuid(ByteString.copyFrom(Utility.UUIDToBytes(fileUUID)))
                            .setFileName(fileName)
                            .setFileSize(fileSize)
                            .build();

            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageSendFile)
                            .setData(sendFileInfo.toByteString())
                            .build();

            sendMessage(message);

        } catch (FileNotFoundException e) {
            delegate.onError(e);
        }
    }

    @Override
    public void onControlActive() {

    }

    @Override
    public void onControlInActive() {
        delegate.onConnect(false);
    }

    @Override
    public void onControlReadMessage(DeviceMessageOuterClass.DeviceMessage message) {
        switch (message.getType()) {
            case kDeviceMessageNone:
                break;
            case kDeviceMessageAckSendFile:
                try {
                    DesktopMessageOuterClass.AckSendFileInfo ack = DesktopMessageOuterClass.AckSendFileInfo.parseFrom(message.getData());
                    UUID uuid = Utility.bytesToUUID(ack.getUuid().toByteArray());
                    Log.d("AN", "Desktop ack send file " + uuid + " pos " + ack.getPos());

                    if (sendFileMap.containsKey(uuid)) {
                        double percentage = desktopDataHandler.sendFile(ack, sendFileMap.get(uuid));
                        delegate.onFileSendProgress(percentage, desktopDataHandler.getByteRate());
                    } else {
                        Log.d("AN", "SendFileMap not contain this uuid " + uuid);
                    }

                } catch (IOException e) {
                    delegate.onError(e);
                }
                break;

            case kDeviceMessageAckSendComplete:
                try {
                    DesktopMessageOuterClass.AckSendFileInfo ack = DesktopMessageOuterClass.AckSendFileInfo.parseFrom(message.getData());
                    UUID uuid = Utility.bytesToUUID(ack.getUuid().toByteArray());

                    delegate.onFileSendComplete();

                } catch (InvalidProtocolBufferException e) {
                    delegate.onError(e);
                }
                break;

            case kDeviceMessageRing:
                delegate.onRing();
                break;

            case kDeviceMessageSendFile:
                try {
                    DesktopMessageOuterClass.SendFileInfo info = DesktopMessageOuterClass.SendFileInfo.parseFrom(message.getData());
                    desktopDataHandler.willReceiveFile(info);
                } catch (InvalidProtocolBufferException e) {
                    delegate.onError(e);
                }
                break;
            case UNRECOGNIZED:
                break;
        }
    }

}
