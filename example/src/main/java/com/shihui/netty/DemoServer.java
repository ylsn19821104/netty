package com.shihui.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class DemoServer {

    public void init(int port) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ServerChannelInitializer());
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

    private class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("decoder", new StringDecoder());
            pipeline.addLast("encoder", new StringEncoder());
            pipeline.addLast("handler", new DemoServerHandler());
        }
    }

    private class DemoServerHandler extends SimpleChannelInboundHandler {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.err.println("Client say : " + msg.toString());
            //返回客户端消息 - 我已经接收到了你的消息
            ctx.writeAndFlush("Received your message : " + msg.toString());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.err.println("RemoteAddress : " + ctx.channel().remoteAddress() + " active !");
            ctx.writeAndFlush("连接成功！");
            super.channelActive(ctx);
        }
    }

    public static void main(String[] args) {
        int port = 8000;
        if (args != null && args.length > 0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {
                //采用默认值
            }
        }
        new DemoServer().init(port);
    }
}
