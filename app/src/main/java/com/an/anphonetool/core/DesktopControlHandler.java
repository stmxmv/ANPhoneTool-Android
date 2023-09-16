package com.an.anphonetool.core;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.an.anphonetool.DesktopMessageOuterClass;
import com.an.anphonetool.DeviceMessageOuterClass;
import com.google.protobuf.GeneratedMessageV3;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
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
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

class DeskControlChannelInitializer extends ChannelInitializer<SocketChannel> {

    DesktopControlHandler handler;

    public DeskControlChannelInitializer(DesktopControlHandler handler) {
        this.handler = handler;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast(new ProtobufVarint32FrameDecoder());
        pipeline.addLast(new ProtobufDecoder(DeviceMessageOuterClass.DeviceMessage.getDefaultInstance()));
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        pipeline.addLast(new ProtobufEncoder());
        pipeline.addLast(handler);
    }
}


public class DesktopControlHandler extends ChannelInboundHandlerAdapter {

    private static int CONTROL_PORT = 13131;
    private static int DATA_PORT = 13132;

    private SocketChannel channel;

    private DesktopControlHandlerDelegate delegate;

    public void setDelegate(DesktopControlHandlerDelegate delegate) {
        this.delegate = delegate;
    }

    public ChannelFuture connect(InetAddress address, @Nullable EventLoopGroup group) {
        if (group == null) {
            group = new NioEventLoopGroup();
        }
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new DeskControlChannelInitializer(this));
        return b.connect(address, CONTROL_PORT);
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
        delegate.onControlActive();;
    }

    @Override
    public void channelInactive(@NonNull ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        delegate.onControlInActive();
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
        super.channelRead(ctx, msg);
        DeviceMessageOuterClass.DeviceMessage message = (DeviceMessageOuterClass.DeviceMessage) msg;

        delegate.onControlReadMessage(message);
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void sendMessage(GeneratedMessageV3 message) {
        if (channel != null) {
            channel.writeAndFlush(message);
        }
    }
}
