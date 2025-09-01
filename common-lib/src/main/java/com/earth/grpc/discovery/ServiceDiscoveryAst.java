package com.earth.grpc.discovery;

import com.earth.grpc.channel.GrpcClientPoolImpl;
import com.earth.grpc.channel.GrpcClientProvider;
import com.earth.grpc.channel.IGrpcClientPool;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
public abstract class  ServiceDiscoveryAst implements ServiceDiscovery {


    private LoadBalancerClient loadBalancerClient;

    /**
     * 设置最大地址重选次数
     */
    public static final int MAX_RECURSION_DEPTH = 10;

    /**
     * 连接被禁用后，多少秒后被shutdown，默认为2秒
     */
    public static final int WAIT_TIME_IN_SEC_TO_KILL_CHANNEL = 2;


    /**
     * 每个 address 最大的缓存 channel 对象
     */
    public static int MAX_CHANNEL_COUNT_PER_SERVICE = 50;


    /**
     * 链接信息，key=应用名称 val=服务实例地址
     * <p>
     * 这个是配置文件上的信息，简单的连接信息，如果是nacos订阅，则不以这个为准
     * k8s的话，是连接其svc服务
     */
    public static final Map<String, Address> STATIC_SERVER_NAME_MAP = new ConcurrentHashMap<>();

    /**
     * 池化 Channel
     * <p>
     * 一个远程应用程序，存在多个实例，每个实例有自己的地址信息
     * <p>
     * 对每个远程应用程序实例创建多个 ManagedChannel 对象,并且对每个 ManagedChannel 开启健康检查，保持探活
     * <p>
     * <a href="https://grpc.io/docs/guides/performance/">grpc官网文档</a>
     * 每个 gRPC 通道使用 0 个或多个 HTTP/2 连接，每个连接 通常对并发流的数量有限制。
     * 当数量 连接上的活动 RPC 达到此限制，其他 RPC 将排队 ，并且必须等待活动 RPC 完成，然后才能发送它们。
     * 具有高负载或长寿命流式处理 RPC 的应用程序可能会看到 由于此队列导致的性能问题
     * <p>
     * nacos下
     * key : channelName (应用名称), address （地址，可多副本）, grpc-channel（池化）
     */
    public static final Map<String, Map<Address, List<ManagedChannel>>> POOLED_CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * 服务实例地址对应拦截器
     */
    private static final Map<IGrpcClientPool.Address, List<ClientInterceptor>> INTERCEPTOR_MAP = new ConcurrentHashMap<>();

    public ServiceDiscoveryAst(LoadBalancerClient loadBalancerClient){
        this.loadBalancerClient = loadBalancerClient;

    }

    /**
     * grpc链接渠道复用 (随机算法)
     * <p>
     * 重复复用渠道信息，如果已存在 channel 则验证 channel connectivityState 如果可以直接使用则返回
     * <p>
     * 如果无法使用则创建新的 channel
     *
     * @param address 服务实例地址
     */
    public ManagedChannel borrowChannel(Address address) {
        try {
            if (ObjectUtils.isEmpty(address)) {
                return null;
            }
            /*
                如果地址已经不可用，不要去池里获取 channel，因为池会被异步销毁
                什么情况下地址会不可用
                1、nacos通知实例注销
                2、池里的连接均不可用，包括兜底建立连接都失败后

                补充逻辑：如果网络闪断，后续修复后，池虽然被销毁，但又会重新自动创建
             */
            if (address.getStatus() == Address.AddressStatusEnum.NotAvailable) {
                Map<IGrpcClientPool.Address, List<ManagedChannel>> addressMap = POOLED_CHANNEL_MAP.get(address.getChannelName());
                if (ObjectUtils.isEmpty(addressMap)) {
                    return null;
                }
                boolean retrySucceeded = Boolean.FALSE;
                // 如果地址是nacos，这里补充一个地址重试逻辑
                if (address.isDiscoveryEnabled()) {
                    // 此处不要想着直接去map中使用其他地址，因为地址是从LB选择出来的，里面包含灰度，权重等逻辑。直接去map中获取会破坏配置的调用链路
                    int addressMaxRecursion = Math.min(addressMap.size(), MAX_RECURSION_DEPTH);
                    Map<IGrpcClientPool.Address, Address.AddressStatusEnum> addressStatusMap = addressMap.keySet().stream().collect(Collectors.toMap(Function.identity(), IGrpcClientPool.Address::getStatus));
                    for (int i = 0; i < addressMaxRecursion; i++) {
                        // 从新从lb拿address无法使用，找个能用的，最大获取次数该addressMap已有地址的数量,但不能超过 MAX_RECURSION_DEPTH 避免性能开销
                        IGrpcClientPool.Address lbAddress = getLBAddress(address.channelName);
                        if (ObjectUtils.isEmpty(lbAddress)) {
                            continue;
                        }
                        // lb给了相同地址，跳过
                        if (address.equals(lbAddress)) {
                            continue;
                        }
                        if (ObjectUtils.isEmpty(addressStatusMap.get(lbAddress))) {
                            continue;
                        }
                        // 地址当前是不可用则跳过
                        if (addressStatusMap.get(lbAddress) == Address.AddressStatusEnum.NotAvailable) {
                            continue;
                        }
                        address = lbAddress;
                        retrySucceeded = Boolean.TRUE;
                        break;
                    }
                }
                // 没有找到后续的可用 address
                if (!retrySucceeded) {
                    return null;
                }
            }

            // 地址状态正常，看下是否需要创建初始化连接
            createPoolChannel(address);
            Map<IGrpcClientPool.Address, List<ManagedChannel>> addressMap = POOLED_CHANNEL_MAP.get(address.getChannelName());
            List<ManagedChannel> managedChannels = addressMap.get(address);
            // 记录请求本次已经随机到的 channel，如果所有 channel 都过了一遍但依然无法找到正常 channel 链接，直接退出
            ArrayList<Integer> visitedList = new ArrayList<>(managedChannels.size());
            for (int i = 0; i < managedChannels.size(); i++) {
                visitedList.add(i);
            }

            // 避免一直随机，通过构建位图，把性能损耗压缩到最大 O(n) ，最低 O(1)
            while (!visitedList.isEmpty()) {
                int index = ThreadLocalRandom.current().nextInt(visitedList.size());
                Integer channelIndex = visitedList.get(index);
                visitedList.remove(index);
                if (ObjectUtils.isEmpty(channelIndex)) {
                    continue;
                }
                ManagedChannel managedChannel = managedChannels.get(channelIndex);
                if (!ObjectUtils.isEmpty(managedChannel)) {
                    ConnectivityState connectivityState = managedChannel.getState(Boolean.FALSE);
                    log.debug("[GRPC] pooledChannelMap retrieve a pooled channel of address {} , connectivityState {}", address, connectivityState);
                    // 验证此链接存活性
                    if (!ObjectUtils.isEmpty(connectivityState)) {
                        if (ConnectivityState.READY == connectivityState
                                || ConnectivityState.IDLE == connectivityState) {
                            return managedChannel;
                        }

                        // 兜底逻辑，如果池里面一个能用的channel都没有，不要再去异步调度，自己进行创建channel使用，并投入池中
                        if (visitedList.isEmpty()) {
                            GrpcClientPoolImpl.DestroyOneChannelTask destroyOneChannelTask = new GrpcClientPoolImpl.DestroyOneChannelTask(address, managedChannel, index);
                            destroyOneChannelTask.run();
                            ManagedChannel newChannel = managedChannels.get(channelIndex);
                            ConnectivityState newConnectivityState = newChannel.getState(Boolean.FALSE);
                            if (ConnectivityState.READY == newConnectivityState
                                    || ConnectivityState.IDLE == newConnectivityState) {
                                return managedChannel;
                            } else {
                                // 如果兜底创建的 channel 都无法连通，证明这个 address 存在网络问题，或者该 address 已经不可用 标记 address 异常
                                address.setStatus(IGrpcClientPool.Address.AddressStatusEnum.NotAvailable);
                                log.error("grpc channel none is available address: {}", address);
                                throw new RuntimeException("grpc channel none is available");
                            }
                        }

                        // 异步-通知调度器销毁掉不可用的 channel，并补充新 channel 进入
                        destroyChannelScheduler.schedule(
                                new GrpcClientPoolImpl.DestroyOneChannelTask(address, managedChannel, index),
                                WAIT_TIME_IN_SEC_TO_KILL_CHANNEL,
                                TimeUnit.SECONDS);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("[GRPC] borrowChannel error", e);
            throw e;
        }
    }


    private IGrpcClientPool.Address getLBAddress(String serverName) {
        // 如果使用注册中心， 通过 loadBalancerClient 从注册中心获取 ServiceInstance 信息 choose 方法可自定义路由灰度逻辑
        ServiceInstance serviceInstance = loadBalancerClient.choose(serverName);
        if (ObjectUtils.isEmpty(serviceInstance)) {
            log.warn("[GRPC] service instance not found, service: {}", serverName);
            return null;
        }
        // 获取服务元数据信息，并检查是否存在 gRPC.port 参数
        Map<String, String> metadata = serviceInstance.getMetadata();
        if (ObjectUtils.isEmpty(metadata.get("gRPC.port"))) {
            log.error("[GRPC] gRPC.port metadata not set, service: {}", serverName);
            return null;
        }

        // 根据元数据获取grpc端口
        int port = Integer.parseInt(metadata.get("gRPC.port"));

        // 构建 Address
        return new Address.AddressBuilder()
                .channelName(serverName)
                .host(serviceInstance.getHost())
                .port(port)
                .discoveryEnabled(Boolean.TRUE)
                .enableHealthCheck(this.grpcClientChannelConfig.isEnableHealthCheck())
                .build();
    }


    /**
     * 创建 channel 链接
     * <p>
     * 可多次创建，不进行 channel 复用，每次都是新的 ManagedChannel
     */
    private ManagedChannel createChannel(IGrpcClientPool.Address address, List<ClientInterceptor> interceptors) {

        ManagedChannelBuilder<?> channel = GrpcClientProvider.createChannel(address.getHost(), address.getPort(), interceptors, 10);
        return channel.build();
    }


    /**
     * 销毁掉有问题的 channel 通道
     * <p>
     * 销毁后，需要新建一个 channel 补充回 pool 中
     */
    @EqualsAndHashCode
    class DestroyOneChannelTask implements Runnable {

        private final IGrpcClientPool.Address address;
        private final ManagedChannel channel;
        private final int index;

        DestroyOneChannelTask(IGrpcClientPool.Address address, ManagedChannel destroyedChannel, int index) {
            this.address = address;
            this.channel = destroyedChannel;
            this.index = index;
        }

        @Override
        public void run() {
            if (ObjectUtils.isEmpty(channel)) {
                return;
            }
            log.debug("[GRPC] destroying a channel of {} ", channel);
            try {
                // 先终止掉 channel
                if (!channel.isShutdown()) {
                    if (!channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)) {
                        // 没成功则 立即 shutdown
                        channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
                    }
                }
            } catch (Exception e) {
                log.error("[GRPC] destroying a channel fail {} ", channel);
            }

            try {
                // 创建新 channel  从池中替换 ，注意要替换 而不是 先 remove 再 add
                if (!ObjectUtils.isEmpty(POOLED_CHANNEL_MAP.get(address.getChannelName()).get(address))) {
                    ManagedChannel managedChannel = POOLED_CHANNEL_MAP.get(address.getChannelName()).get(address).get(index);
                    if (!ObjectUtils.isEmpty(managedChannel)) {
                        if (managedChannel.isShutdown()) {
                            synchronized (DestroyOneChannelTask.class) {
                                if (managedChannel.isShutdown()) {
                                    ManagedChannel newChannel = createChannel(address, INTERCEPTOR_MAP.get(address));
                                    POOLED_CHANNEL_MAP.get(address.getChannelName()).get(address).set(index, newChannel);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[GRPC] destroyOneChannelTask supplement channel error", e);
            }
        }
    }
}
