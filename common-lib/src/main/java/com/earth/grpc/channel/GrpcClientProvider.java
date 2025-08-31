/*
 ************************************
 * @项目名称: btg-shard-lib
 * @文件名称: GrpcClientProvider
 * @Date 2018/07/20
 * @Author toyoda@bitgroup.com
 * @Copyright（C）: 2018 BitCloudGroup Inc.   All rights reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 **************************************
 */
package com.earth.grpc.channel;


import com.earth.grpc.context.GrpcHeaderClientInterceptor;
import com.earth.grpc.context.Transport;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.InternalNettyChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.*;

/**
 * GrpcClientProvider
 * <p>
 * Grpc客户端提供程序,初始化GRPC客户端类
 *
 * @author luke
 * @version 2.0.0
 */
@Slf4j
public class GrpcClientProvider {

    private GrpcClientProvider() {
    }

    @Getter
    private static volatile ThreadPoolExecutor grpcClientExecutor;

    private static final String THREAD_POOL_EXECUTOR_GRPC_APP = "grpc-client-app-%d";

    private static final String OS = System.getProperty("os.name").toLowerCase();

    private static final DefaultThreadFactory tf = new DefaultThreadFactory("grpc-client-worker", true);

    private static volatile EventLoopGroup eventLoopGroup;

    private static volatile Class<? extends Channel> channelType;


    private final static GrpcHeaderClientInterceptor GRPC_HEADER_CLIENT_INTERCEPTOR = new GrpcHeaderClientInterceptor();


    /**
     * @param clientWorkerCount Client Business worker count, 0 means default = CPU cores * 2
     */
    public static ManagedChannelBuilder<?> createChannel(String hostname, int port, List<ClientInterceptor> interceptors, int clientWorkerCount) {
        return createChannel(hostname, port, Transport.NETTY_EPOLL, interceptors, clientWorkerCount);
    }




    /**
     * grpc-client-app 线程池创建
     * <p>
     * The following parameters are used to configure the thread pool for the gRPC client application, ensuring efficient task execution and resource management.
     * <p>
     * Typical parameters
     * parallelism = 200,
     * corePool = parallelism = 200, always keep 200 thread there
     * queue size = parallelism * 2 = 400, if 200 broker pool thread all busy, keep at most 400 task waiting in queue
     * maximumPoolSize = parallelism * 3 = 600, if 400 task waiting in queue, it means we have to expand
     * pool size, so create new thread when new task comes. at most up to 600 threads
     * reject policy = DiscardOldestPolicy, if queue 400 full, thread 600 full, and still new task comes,
     * <p>
     * 拒绝策略，如果没有线程数进行执行，则调用线程执行任务
     */
    private static ExecutorService createGrpcClientThreadPoolExecutor(int clientAppCount) {
        // grpcClientExecutor 全局唯一，grpc 请求均使用此线程池进行远程调用
        if (ObjectUtils.isEmpty(grpcClientExecutor)) {
            synchronized (GrpcClientProvider.class) {
                if (ObjectUtils.isEmpty(grpcClientExecutor)) {
                    // 并行度值
                    int parallelism;

                    // 如果配置了并行度则以设置的为准
                    if (clientAppCount > 0) {
                        parallelism = clientAppCount;
                    } else {
                        // 如果没有设置线程数，默认是当前系统CPU核心数*2
                        int coreSize = Runtime.getRuntime().availableProcessors();
                        parallelism = coreSize * 2;
                    }


                    // 初始化线程池参数, 并行度最小为 10  队列最小 200 ，队列数默认为 并行度的 10 倍大小
                    final int corePoolSize = Math.max(parallelism, 10);
                    final int maximumPoolSize = Math.max(parallelism * 2, 20);
                    final long keepAliveTime = 60L;
                    final int queueMaxLength = Math.max(parallelism * 10, 200);

                    log.info("[GRPC] grpc client thread pool corePoolSize : {} maximumPoolSize : {} queueMaxLength : {}",
                            corePoolSize,
                            maximumPoolSize,
                            queueMaxLength);

                    ThreadFactory threadFactory = GrpcUtil.getThreadFactory(THREAD_POOL_EXECUTOR_GRPC_APP, true);

                    grpcClientExecutor = new ThreadPoolExecutor(
                            corePoolSize,
                            maximumPoolSize,
                            keepAliveTime,
                            TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(queueMaxLength),
                            threadFactory,
                            new ThreadPoolExecutor.CallerRunsPolicy());

                    // 预启动所有核心线程
                    grpcClientExecutor.prestartAllCoreThreads();
                    return grpcClientExecutor;
                }
            }
        }
        return grpcClientExecutor;
    }


    /**
     * 新创建一个 NETTY_EPOLL model 的 channel builder
     *
     * @param clientWorkerCount Client Business worker count, 0 means default = CPU cores * 2
     */
    public static ManagedChannelBuilder<?> createChannel(String hostname, int port, Transport transport, List<ClientInterceptor> interceptors, int clientWorkerCount) {

        /*
            1、创建socket连接
            2、根据系统环境构建netty服务，优先使用 NETTY_EPOLL 模式
            3、设置明文传输以及最大报文大小
            4、设置拦截器
         */
        InetSocketAddress address = new InetSocketAddress(hostname, port);

        ManagedChannelBuilder<?> builder;
        if (OS.contains("mac")) {
            builder = ManagedChannelBuilder.forAddress(hostname, port);
        } else if (OS.contains("win")) {
            builder = newNettyClientChannel(Transport.NETTY_NIO, address);
        } else {
            builder = newNettyClientChannel(transport, address);
        }
        // 构建处理线程池
        builder.executor(createGrpcClientThreadPoolExecutor(clientWorkerCount));
        builder.intercept(GRPC_HEADER_CLIENT_INTERCEPTOR);
        // you can set to useSsl later
        builder.usePlaintext();
        builder.maxInboundMessageSize(8 * 1024 * 1024);

        // 设置拦截器
        if (interceptors != null) {
            builder.intercept(interceptors);
        }
        return builder;
    }

    private static ManagedChannelBuilder<?> newNettyClientChannel(Transport transport, SocketAddress address) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(address);

        if (eventLoopGroup == null || channelType == null) {
            synchronized (GrpcClientProvider.class) {
                if (eventLoopGroup == null || channelType == null) {
                    switch (transport) {
                        case NETTY_NIO:
                            eventLoopGroup = new NioEventLoopGroup(0, tf);
                            channelType = NioSocketChannel.class;
                            break;
                        case NETTY_EPOLL:
                            if (Epoll.isAvailable()) { // linux
                                eventLoopGroup = new EpollEventLoopGroup(0, tf);
                                channelType = EpollSocketChannel.class;
                            } else if (KQueue.isAvailable()) { // mac
                                eventLoopGroup = new KQueueEventLoopGroup(0, tf);
                                channelType = KQueueSocketChannel.class;
                            }
                            break;
                        case NETTY_UNIX_DOMAIN_SOCKET:
                            eventLoopGroup = new EpollEventLoopGroup(0, tf);
                            channelType = EpollDomainSocketChannel.class;
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported transport: " + transport);
                    }
                }
            }
        }
        builder.eventLoopGroup(eventLoopGroup).channelType(channelType);
        InternalNettyChannelBuilder.setStatsEnabled(builder, false);
        InternalNettyChannelBuilder.setTracingEnabled(builder, false);
        return builder;
    }


}
