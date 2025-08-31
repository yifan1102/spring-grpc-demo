package com.earth.grpc.channel;


import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.*;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Set;

/**
 * 一个支持各种系统能力的Grpc Client Pool. 支持的能力包括Load Balance, Full chain tracing等.
 *
 * <p></p>
 * <strong>每次需要使用一个Channel对象时，请直接从该Pool中borrow，勿需对Pool中得到的channel对象进行保存。</strong>
 * <p></p>
 * 这些channel对象，默认使用epoll模型
 * 从该pool中获取的Channel对象的生命周期完全由pool本身维护。
 */
@ThreadSafe
public interface IGrpcClientPool {

    /**
     * 通过host与port得到一个channel。每次调用该方法返回的channel对象可能并不是同一个.因此，请
     * 勿在client程序中维护该channel的生命周期。<p/>
     * 该方法原本应该抛出一个异常， 但为了保持方法签名的兼容性，以返回null表示遇到错误。特别是在
     * checkMode设置为4时。
     *
     * @param host 服务器地址
     * @param port 服务器端口
     * @return 一个可用的channel对象, 如果在borrowChannel的过程中，遇到错误，会返回一个Null对象。
     */
    ManagedChannel borrowChannel(String host, int port);


    /**
     * 通过key得到一个channel。每次调用该方法返回的channel对象可能并不是一个.因此，请勿在client
     * 程序中维护该channel的生命周期。<p/>
     * 该方法原本应该抛出一个异常， 但为了保持方法签名的兼容性，以返回null表示遇到错误。特别是在
     * checkMode设置为4时。
     *
     * @param serverName 通过 setShortcut维护的一个快捷列表。shortcut<--->host:port
     * @return 一个可用的channel对象, 如果在borrowChannel的过程中，遇到错误，会返回一个Null对象。
     */
    ManagedChannel borrowChannel(String serverName);

    /**
     * 通过key得到一个channel。该channel不用归还，你可以在client
     * 程序中维护该channel的生命周期。pool对象不再对该对象的生命周期负责。通常该对象用于grpc的stream处理操作
     * 另外，该channel不再享有load balance的能力，但会获得其他pool中支持的系统能力。<p/>
     * 该方法原本应该抛出一个异常， 但为了保持方法签名的兼容性，以返回null表示遇到错误。特别是在
     * checkMode设置为4时。
     *
     * @param shortcut 通过 setShortcut维护的一个快捷列表。shortcut<--->host:port
     * @return 一个可用的channel对象, 如果在robChannel的过程中，遇到错误，会返回一个Null对象。
     */
    ManagedChannel robChannel(String shortcut);

    /**
     * 通过key创建一个channel， 该channel不会从pool获取， 仅仅是通过pool创建， 每次调用该方法， 总会获得一个
     * 新的channel对象，这是区别于<code>robChannel</code>的地方。另外，该channel也不会被Pool维护，这一点与
     * <code>robChannel</code>类似.<p/>
     * 该方法原本应该抛出一个异常， 但为了保持方法签名的兼容性，以返回null表示遇到错误。特别是在
     * checkMode设置为4时。
     *
     * @param shortcut 通过 setShortcut维护的一个快捷列表。shortcut<--->host:port
     * @return 一个可用的channel对象, 如果在newChannel的过程中，遇到错误，会返回一个Null对象。
     */
    ManagedChannel newChannel(String shortcut);

    /**
     * 通知 pool 这个 channel 坏了
     */
    @Deprecated
    void returnBrokenChannel(ManagedChannel channel) throws NullPointerException;

    /**
     * 设置grpc调用访问快捷链接
     *
     * <p>
     * 为了方便进行pool对象的维护，所设置的一个快捷列表. applicationName -> host:port
     *
     * @param applicationName 远程调用服务应用名称
     * @param host            grpc服务端地址信息
     * @param port            grpc服务端端口信息
     * @param discoverEnabled 是否启用注册中心订阅，如果启用 true 则 host,port 不再生效，而是通过注册中心返回的实例信息进行链接创建
     */
    void setShortcut(String applicationName, String host, int port, boolean discoverEnabled);

    /**
     * 为指定的client追加一个client interceptor, interceptor的顺序由调用该方法的顺序决定
     *
     * @param key 来源于setShortcut方法
     */
    void appendClientInterceptor(String key, ClientInterceptor interceptor);


    /**
     * 实现你自己配置，其优先级最高，会覆盖默认配置
     */
    void acceptCustomClientConfig(String key, ClientConfigCallback configCallback);

    /**
     * 返回shortcut所指示的地址信息
     */
    Address addressOf(String shortcut);

    /**
     * 清除已经缓存的连接对象
     */
    void clearPooledObject(String shortcut);

    /**
     * 清除已经缓存的连接对象
     */
    void clearPooledObject(Address address);

    /**
     * 延迟清除已经缓存的连接对象
     */
    void delayClearPooledObject(Address address);

    /**
     * 返回当前pool中已经支持的shortcut列表
     */
    Set<String> shortcuts();

    /**
     * 关闭pool对象，会释放该pool中所有的对象
     */
    void shutdown();



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
         * 是否启动nacos注册
         * <p>
         * true 使用 nacos
         * false 使用 k8s svc
         */
        boolean discoveryEnabled;

        /**
         * host
         */
        String host;

        /**
         * port
         */
        int port;



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