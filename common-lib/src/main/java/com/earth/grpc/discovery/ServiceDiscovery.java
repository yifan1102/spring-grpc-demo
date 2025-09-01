package com.earth.grpc.discovery;

import com.earth.grpc.channel.GrpcClientPoolImpl;
import com.earth.grpc.channel.IGrpcClientPool;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.*;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ServiceDiscovery {


    /**
     * 通过host与port得到一个channel。每次调用该方法返回的channel对象可能并不是同一个.因此，请
     * 勿在client程序中维护该channel的生命周期。<p/>
     * 该方法原本应该抛出一个异常， 但为了保持方法签名的兼容性，以返回null表示遇到错误。特别是在
     * checkMode设置为4时
     * @param host 服务器地址
     * @param port 服务器端口
     * @return 一个可用的channel对象, 如果在borrowChannel的过程中，遇到错误，会返回一个Null对象。
     */
    ManagedChannel borrowChannel(String host, int port);


    /**
     * 通过key得到一个channel。每次调用该方法返回的channel对象可能并不是一个.因此，请勿在client
     * 程序中维护该channel的生命周期。<p/>
     * 该方法原本应该抛出一个异常， 但为了保持方法签名的兼容性，以返回null表示遇到错误。特别是在
     * checkMode设置为4时
     * @param serverName 通过 setShortcut维护的一个快捷列表。shortcut<--->host:port
     * @return 一个可用的channel对象, 如果在borrowChannel的过程中，遇到错误，会返回一个Null对象。
     */
    ManagedChannel borrowChannel(String serverName);



    /**
     * 实现你自己配置，其优先级最高，会覆盖默认配置
     */
    void acceptCustomClientConfig(String key, ClientConfigCallback configCallback);


    interface ClientConfigCallback {

        void customClientConfig(ManagedChannelBuilder<?> builder);

    }


    @Builder
    @EqualsAndHashCode(of = {"channelName", "host", "port"})
    @Getter
    @ToString
    class Address {

        /**
         * 渠道名称
         */
        String channelName;

        /**
         * host
         */
        String host;

        /**
         * port
         */
        int port;

        /**
         * 是否启动nacos注册
         * <p>
         * true 使用 nacos
         * false 使用 k8s svc
         */
        boolean discoveryEnabled;

        /**
         * 是否开启健康检查地址存活
         * <p>
         * true 开启后10s检查下面的channel池状态
         * false 不对channel池连接状态进行检查
         * <p>
         * 固有逻辑，grpc请求前都会验证channel连接状态，设置true和false都不会影响这个逻辑
         */
        boolean enableHealthCheck;

        /**
         * 地址健康状态
         * <p>
         * 不可用的地址下的连接池将会被销毁掉
         */
        @Setter
        AddressStatusEnum status;

        @Getter
        @AllArgsConstructor
        enum AddressStatusEnum {

            Health,
            NotAvailable

        }

    }



}
