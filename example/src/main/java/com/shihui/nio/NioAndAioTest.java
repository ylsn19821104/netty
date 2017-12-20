package com.shihui.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

public class NioAndAioTest {
    private static ExecutorService workers = Executors.newFixedThreadPool(10);
    private static InetSocketAddress LOCAL_8080 = new InetSocketAddress(8080);

    /**
     * 解析出来的请求对象
     */
    public static class TestRequest {
        /**
         * 根据解析到的method来获取响应的Handler
         */
        String method;
        String args;

        public static TestRequest parseFromString(String req) {
            System.out.println("收到请求：" + req);
            TestRequest request = new TestRequest();
            request.method = req.substring(0, 512);
            request.args = req.substring(512, req.length());
            return request;
        }
    }

    private static void userNio() {
        Selector dispatcher = null;
        ServerSocketChannel serverChannel = null;
        try {
            dispatcher = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);

            serverChannel.socket().setReuseAddress(true);
            serverChannel.socket().bind(LOCAL_8080);
            //ServerSocketChannel只支持这一种key，因为server端的socket只能去accept
            serverChannel.register(dispatcher, SelectionKey.OP_ACCEPT);

            while (dispatcher.select() > 0) {
                operate(dispatcher);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 在分发器上循环获取连接事件
     *
     * @param dispatcher
     * @throws IOException
     */
    private static void operate(Selector dispatcher) throws IOException {
        Set<SelectionKey> keys = dispatcher.keys();
        Iterator<SelectionKey> ki = keys.iterator();
        while (ki.hasNext()) {
            SelectionKey key = ki.next();
            ki.remove();
            if (key.isAcceptable()) {
                ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                final SocketChannel client = channel.accept();
                workers.submit(() -> {
                    try {
                        TestRequest request = TestRequest.parseFromString(parse(client));
                        SockerServerHandler handler = null;//(SockerServerHandler) Class.forName(getClassNameForMethod(request.method)).newInstance();
                        client.write(handler.handle(request));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
            }
        }
    }

    private static String parse(SocketChannel socket) throws IOException {
        String req = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            byte[] bytes;
            int count = 0;
            if ((count = socket.read(buffer)) >= 0) {
                buffer.flip();
                bytes = new byte[count];
                buffer.get(bytes);
                req = new String(bytes, Charset.forName("utf-8"));
                buffer.clear();
            }
        } finally {
            socket.socket().shutdownInput();
        }
        return req;
    }

    private static void useAIO() {
        AsynchronousServerSocketChannel server;
        try {
            server = AsynchronousServerSocketChannel.open();
            server.bind(LOCAL_8080);
            while (true) {
                Future<AsynchronousSocketChannel> socketF = server.accept();
                try {
                    final AsynchronousSocketChannel socket = socketF.get();
                    workers.submit(() -> {
                        try {
                            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
                            socket.read(buffer, null, new CompletionHandler<Integer, Object>() {
                                @Override
                                public void completed(Integer count, Object attachment) {
                                    byte[] bytes;
                                    if (count >= 0) {
                                        buffer.flip();
                                        bytes = new byte[count];
                                        buffer.get(bytes);
                                        String req = new String(bytes, Charset.forName("utf-8"));
                                        try {
                                            TestRequest request = TestRequest.parseFromString(req);
                                            SockerServerHandler handler = null;//(SockerServerHandler) Class.forName(getClassNameForMethod(request.method)).newInstance();
                                            ByteBuffer bb = handler.handle(request);
                                            socket.write(bb, null, null);
                                        } catch (Exception e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }
                                        buffer.clear();
                                    }
                                }

                                @Override
                                public void failed(Throwable exc, Object attachment) {
                                    // TODO Auto-generated method stub
                                }
                            });

                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } finally {
                        }
                    });
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 具体的逻辑需要实现此接口
     */
    interface SockerServerHandler {
        ByteBuffer handle(TestRequest req);
    }


    private volatile static int succ = 0;

    public static void main(String[] args) throws UnknownHostException, IOException {
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                Socket soc;
                try {
                    soc = new Socket("localhost", 8080);
                    if (soc.isConnected()) {
                        OutputStream out = soc.getOutputStream();
                        byte[] req = "hello".getBytes("utf-8");
                        out.write(Arrays.copyOf(req, 1024));
                        InputStream in = soc.getInputStream();
                        byte[] resp = new byte[1024];
                        in.read(resp, 0, 1024);
                        String result = new String(resp, "utf-8");
                        if (result.equals("haha")) {
                            succ++;
                        }
                        System.out.println(Thread.currentThread().getName() + "收到回复:" + result);
                        out.flush();
                        out.close();
                        in.close();
                        soc.close();
                    }
                    try {
                        System.out.println(Thread.currentThread().getName() + "去睡觉等待。。。");
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            latch.countDown();
        }
        Runnable hook = () -> System.out.println("成功个数：" + succ);
        Runtime.getRuntime().addShutdownHook(new Thread(hook));
    }


}
