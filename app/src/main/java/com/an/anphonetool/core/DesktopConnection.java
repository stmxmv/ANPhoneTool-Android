package com.an.anphonetool.core;

import androidx.annotation.NonNull;
import com.an.anphonetool.DesktopMessageOuterClass;
import com.google.protobuf.GeneratedMessageV3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class DesktopConnection {

    private static int CONTROL_PORT = 13131;
    private static int DATA_PORT = 13132;

    private Socket controlSocket;
    private Socket dataSocket;
    private InputStream controlInputStream;
    private OutputStream controlOutputStream;

    private InputStream dataInputStream;
    private OutputStream dataOutputStream;


    public DesktopConnection(Inet4Address address) throws IOException {
        Socket controlSocket = new Socket(address, CONTROL_PORT);
        Socket dataSocket = new Socket(address, DATA_PORT);
        init(controlSocket, dataSocket);
    }

    private DesktopConnection(@NonNull Socket controlSocket, @NonNull Socket dataSocket) throws IOException {
        init(controlSocket, dataSocket);
    }

    private void init(@NonNull Socket controlSocket, @NonNull Socket dataSocket) throws IOException {
        this.controlSocket = controlSocket;
        this.dataSocket = dataSocket;
        controlInputStream = controlSocket.getInputStream();
        controlOutputStream = controlSocket.getOutputStream();

        dataInputStream = dataSocket.getInputStream();
        dataOutputStream = dataSocket.getOutputStream();
    }

    public void close() throws IOException {
        if (controlSocket != null) {
            controlSocket.shutdownInput();
            controlSocket.shutdownOutput();
            controlSocket.close();
        }
    }

    /// FIXME: not send data in ui thread!!
    public void sendMessage(GeneratedMessageV3 message) throws IOException {
        byte[] bytes = message.toByteArray();
        int size = bytes.length + 4; // 4 is the int size

        controlOutputStream.write(Utility.intToBytes(size));
        controlOutputStream.write(message.toByteArray());
    }



}
