package com.earth.grpc.channel;

import lombok.Data;

@Data
public class GrpcClientChannelConfig {


    // 是否启动 channel 健康检查 默认 true
    private boolean enableHealthCheck = Boolean.TRUE;
}
