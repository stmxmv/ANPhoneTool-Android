package com.an.anphonetool.core;

public class Utility {

    static public byte[] intToBytes(int value) {
        byte[] byteArray = new byte[4];

        // Use bitwise operations and byte shifting to convert the integer to a byte array
        byteArray[0] = (byte) (value & 0xFF);
        byteArray[1] = (byte) ((value >> 8) & 0xFF);
        byteArray[2] = (byte) ((value >> 16) & 0xFF);
        byteArray[3] = (byte) ((value >> 24) & 0xFF);
        return byteArray;
    }

}
