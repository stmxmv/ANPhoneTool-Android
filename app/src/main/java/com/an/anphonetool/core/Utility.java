package com.an.anphonetool.core;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public class Utility {


    /// my complain: I hate java for I cannot use pointer to direct cast to int or long
    static public byte[] intToBytes(int value) {
        byte[] byteArray = new byte[4];

        // Use bitwise operations and byte shifting to convert the integer to a byte array
        byteArray[0] = (byte) (value & 0xFF);
        byteArray[1] = (byte) ((value >> 8) & 0xFF);
        byteArray[2] = (byte) ((value >> 16) & 0xFF);
        byteArray[3] = (byte) ((value >> 24) & 0xFF);
        return byteArray;
    }

    public static byte[] longToBytes(long value) {
        byte[] result = new byte[8]; // A long is 8 bytes

        for (int i = 0; i < 8; ++i) {
            result[i] = (byte) (value & 0xFF); // Extract the least significant byte
            value >>= 8; // Shift the value to the right by 8 bits
        }

        return result;
    }

    public static long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 7; i >= 0; --i) {
            result <<= 8;
            result |= (bytes[i] & 0xFF); // Extract the least significant byte
        }
        return result;
    }

    public static String getFileNameFromUri(ContentResolver contentResolver, Uri uri) {
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String fileName = cursor.getString(nameIndex);
            cursor.close();
            return fileName;
        }

        return null; // Return null if the file name couldn't be determined
    }

    public static UUID bytesToUUID(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }

    public static byte[] UUIDToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

}
