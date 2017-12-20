package com.shihui.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DemoClient {
    public static String host = "127.0.0.1"; //服务器IP地址
    public static int port = 8000; //服务器端口

    /**
     * 客户端Channel通道初始化设置
     */
    static class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
            ChannelPipeline pipeline = socketChannel.pipeline();
            //字符串解码和编码
            pipeline.addLast("decoder", new StringDecoder());
            pipeline.addLast("encoder", new StringEncoder());
            //客户端的逻辑
            pipeline.addLast("handler", new DemoClientHandler());
        }
    }

    /**
     * 客户端业务逻辑
     */
    static class DemoClientHandler extends SimpleChannelInboundHandler {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.err.println("Server say : " + msg.toString());
        }
    }

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientChannelInitializer());

            //连接客户端
            Channel channel = b.connect(host, port).sync().channel();
            //控制台输入
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            for (; ; ) {
                String line = in.readLine();
                if (line == null) {
                    continue;
                }
                //向服务端发送数据
                channel.writeAndFlush(line);
            }
        } finally {
            //优雅退出，释放线程池资源
            group.shutdownGracefully();
        }
    }
}
