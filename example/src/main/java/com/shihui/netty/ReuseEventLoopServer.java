package com.shihui.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

public class ReuseEventLoopServer {
    public void init(int port) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 751024)
                    .childHandler(new SimpleChannelInboundHandler<SocketChannel>() {
                        ChannelFuture future;

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            Bootstrap bootstrap = new Bootstrap();
                            bootstrap.channel(NioSocketChannel.class)
                                    .handler(new SimpleChannelInboundHandler<ByteBuf>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                                            System.err.println("Receive data:" + in.toString());
                                        }
                                    });
                            bootstrap.group(ctx.channel().eventLoop());
                            future = bootstrap.connect(new InetSocketAddress("www.baidu.com", 80));
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, SocketChannel msg) throws Exception {
                            if (future.isDone()) {
                                System.err.println("Receive data done!");
                            }
                        }
                    });
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        new ReuseEventLoopServer().init(8000);
    }
}
