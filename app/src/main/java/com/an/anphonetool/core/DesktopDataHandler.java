package com.an.anphonetool.core;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.an.anphonetool.DesktopMessageOuterClass;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

class DesktopDataBlock {
    public UUID uuid;
    public long blockSize;
    public byte[] block;
}

class DesktopDataEncoder extends MessageToByteEncoder<DesktopDataBlock> {
    @Override
    protected void encode(ChannelHandlerContext ctx, DesktopDataBlock msg, ByteBuf out) throws Exception {
        out.ensureWritable(24 + msg.block.length); // UUID 16 + size_t 8 = 24
        out.writeBytes(Utility.UUIDToBytes(msg.uuid));
        out.writeBytes(Utility.longToBytes(msg.blockSize));
        out.writeBytes(msg.block);
    }
}

class DesktopDataDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();
        if (in.readableBytes() >= 24) {
            DesktopDataBlock dataBlock = new DesktopDataBlock();

            byte[] uuidBytes = new byte[16];
            in.readBytes(uuidBytes);

            dataBlock.uuid = Utility.bytesToUUID(uuidBytes);

            byte[] blockSizeBytes = new byte[8];
            in.readBytes(blockSizeBytes);
            dataBlock.blockSize = Utility.bytesToLong(blockSizeBytes);

            if (in.readableBytes() < dataBlock.blockSize) {
                in.resetReaderIndex();
            } else {
                dataBlock.block = new byte[(int)dataBlock.blockSize];
                in.readBytes(dataBlock.block);
                out.add(dataBlock);
            }
        }
    }
}
class DesktopDataChannelInitializer extends ChannelInitializer<SocketChannel> {

    DesktopDataHandler handler;

    public DesktopDataChannelInitializer(DesktopDataHandler handler) {
        this.handler = handler;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast(new DesktopDataDecoder());
        pipeline.addLast(new DesktopDataEncoder());
        pipeline.addLast(handler);
    }
}

class ReceiveFileContext {
    public DesktopMessageOuterClass.SendFileInfo info;
    public File file;
    public OutputStream fileOutputStream;
    public long receivedSize = 0;
    public boolean complete = false;
}

public class DesktopDataHandler extends ChannelInboundHandlerAdapter {

    private DesktopControlHandler controlHandler;
    private  SocketChannel channel;
    private static int DATA_PORT = 13132;

    private static int BLOCK_SIZE = 1024 * 1024; // 1MB block size

    private long lastTransferBytes = 0;
    private long lastTime = 0;
    private double byteRate = 0;

    private HashMap<UUID, ReceiveFileContext> receiveFileMap = new HashMap<>();

    public void setControlHandler(DesktopControlHandler controlHandler) {
        this.controlHandler = controlHandler;
    }

    public ChannelFuture connect(Inet4Address address, @Nullable EventLoopGroup group) {
        if (group == null) {
            group = new NioEventLoopGroup();
        }
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new DesktopDataChannelInitializer(this));
        return b.connect(address, DATA_PORT);
    }

    public void stop() {
        if (channel != null) {
            channel.shutdown();
            channel.close();
        }
    }

    @Override
    public void channelActive(@NonNull ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        synchronized (this) {
            channel = (SocketChannel)ctx.channel();
        }
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public double getByteRate() {
        return byteRate;
    }

    /// return percentage
    public double sendFile(DesktopMessageOuterClass.AckSendFileInfo ack, String path) throws IOException {
        DesktopDataBlock block = new DesktopDataBlock();
        block.uuid = Utility.bytesToUUID(ack.getUuid().toByteArray());

        File file = new File(path);
        RandomAccessFile accessFile;
        if (file.exists()) {
            accessFile = new RandomAccessFile(file, "r");
        } else {
            Log.d("AN", "File not exist");
            return 0.0;
        }

        if (channel != null) {
            accessFile.seek(ack.getPos());
            byte[] bytes = new byte[BLOCK_SIZE];
            int readSize = accessFile.read(bytes);

            if (readSize <= 0) {
                accessFile.close();
                return 0.0;
            }

            block.blockSize = Math.min(readSize, BLOCK_SIZE);

            if (readSize < BLOCK_SIZE) {
                block.block = new byte[(int)block.blockSize];
                System.arraycopy(bytes, 0, block.block, 0, readSize);
            } else {
                block.block = bytes;
            }

            accessFile.close();
            channel.writeAndFlush(block);


            long currentTime = System.currentTimeMillis();
            long deltaTime = currentTime - lastTime;
            lastTime = currentTime;

            double deltaTimeSeconds = (double)deltaTime / 1000.0;
            byteRate = (double)lastTransferBytes / deltaTimeSeconds;

            lastTransferBytes = block.blockSize;

            return (double)(ack.getPos()) / (double)file.length();
        }



        return 0.0;
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
        super.channelRead(ctx, msg);
        DesktopDataBlock dataBlock = (DesktopDataBlock) msg;

        if (receiveFileMap.containsKey(dataBlock.uuid)) {

            ReceiveFileContext context = receiveFileMap.get(dataBlock.uuid);
            context.receivedSize += dataBlock.blockSize;

            context.fileOutputStream.write(dataBlock.block);

            if (context.receivedSize >= context.info.getFileSize()) {
                context.complete = true;

                context.fileOutputStream.flush();
                context.fileOutputStream.close();

                /// ack complete
                /// ack block
                DesktopMessageOuterClass.AckSendFileInfo ack =
                        DesktopMessageOuterClass.AckSendFileInfo
                                .newBuilder()
                                .setUuid(ByteString.copyFrom(Utility.UUIDToBytes(dataBlock.uuid)))
                                .setPos(context.receivedSize)
                                .build();

                DesktopMessageOuterClass.DesktopMessage message =
                        DesktopMessageOuterClass.DesktopMessage.newBuilder()
                                .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageAckSendComplete)
                                .setData(ack.toByteString())
                                .build();

                controlHandler.sendMessage(message);

                Log.d("AN", "receive file complete");

            } else {
                /// ack block
                DesktopMessageOuterClass.AckSendFileInfo ack =
                        DesktopMessageOuterClass.AckSendFileInfo
                                .newBuilder()
                                .setUuid(ByteString.copyFrom(Utility.UUIDToBytes(dataBlock.uuid)))
                                .setPos(context.receivedSize)
                                .build();

                DesktopMessageOuterClass.DesktopMessage message =
                        DesktopMessageOuterClass.DesktopMessage.newBuilder()
                                .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageAckSendFile)
                                .setData(ack.toByteString())
                                .build();

                controlHandler.sendMessage(message);

                Log.d("AN", "receive file ack sent");
            }

        } else {
            Log.d("AN", "receive unknown block uuid " + dataBlock.uuid);
        }
    }

    public void willReceiveFile(DesktopMessageOuterClass.SendFileInfo info) {
        UUID uuid = Utility.bytesToUUID(info.getUuid().toByteArray());

        ReceiveFileContext context = new ReceiveFileContext();
        context.info = info;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // Get the default download directory
            String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            String filePath = downloadPath + File.separator + info.getFileName();

            context.file = new File(filePath);
            try {
                context.fileOutputStream = new FileOutputStream(context.file);
            } catch (FileNotFoundException e) {
                Log.d("AN", "cannot create download file");
                return;
            }

            receiveFileMap.put(uuid, context);

            Log.d("AN", "file will download at path " + filePath);

            DesktopMessageOuterClass.AckSendFileInfo ackSendFileInfo =
                    DesktopMessageOuterClass.AckSendFileInfo.newBuilder()
                            .setUuid(ByteString.copyFrom(Utility.UUIDToBytes(uuid)))
                            .setPos(0)
                            .build();

            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageAckSendFile)
                            .setData(ackSendFileInfo.toByteString())
                            .build();

            controlHandler.sendMessage(message);
        } else {

            Log.d("AN", "cannot get the download folder path");
        }
    }
}
