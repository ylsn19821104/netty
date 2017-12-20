package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class NettyTimerServer {
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(new NettyTimeServerHandler());
                        }
                    });
            //对于每一个端口的监听,会有一个单独的线程(Boss)去监听并处理其I/O事件
            //Boss线程为这个事件生成对应的channel,并绑定其对应的pipeline,然后交给Worker
            //Worker会将此Channel绑定到某个EventLoop(I/O线程)上,之后所有这个Channel上的事件默认都要在EventLoop上执行

            //当事件需要执行耗时的工作时，为了不阻塞I/O线程，往往会自定义一个EventExecutorGroup
            // （Netty4提供了io.netty.util.concurrent.DefaultEventExecutorGroup），将耗时的Handler放入其中执行

            //对于没有指定EventExecutorGroup的Handler，将默认指定为Channel上绑定的EventLoop
            ChannelFuture f = b.bind(PORT).sync();
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
