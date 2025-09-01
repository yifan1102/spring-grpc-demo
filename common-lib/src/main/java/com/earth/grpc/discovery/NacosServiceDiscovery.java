package com.earth.grpc.discovery;

import com.earth.grpc.channel.IGrpcClientPool;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;



@Slf4j
public class NacosServiceDiscovery extends ServiceDiscoveryAst {



    @Override
    public ManagedChannel borrowChannel(String host, int port) {
        return null;
    }

    @Override
    public ManagedChannel borrowChannel(String serverName) {
        Address address = STATIC_SERVER_NAME_MAP.get(serverName);
        if (Objects.isNull(address)) {
            log.info("[GRPC] address not found, service:{}", serverName);
            return null;
        }
        // 是否通过注册中心获取 channel 如果不是直接连接该地址 一般为 k8s svc
        if (!address.isDiscoveryEnabled()) {
            // 直接创建裸连接
            return borrowChannel(address);
        }
        return null;
    }

    @Override
    public void acceptCustomClientConfig(String key, ClientConfigCallback configCallback) {

    }
}
