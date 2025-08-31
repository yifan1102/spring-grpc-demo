package com.earth.grpc.context;

import com.alibaba.fastjson.JSON;
import io.grpc.*;
import org.springframework.util.ObjectUtils;

public class GrpcHeaderClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT,RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (!ObjectUtils.isEmpty(GrpcHeaderContext.getHeader())) {
                    // 在这里向 headers 中添加自定义的元数据
                    Metadata.Key<String> key = Metadata.Key.of("grpc-header", Metadata.ASCII_STRING_MARSHALLER);
                    headers.put(key, JSON.toJSONString(GrpcHeaderContext.getHeader()));
                }
                // 调用下一个拦截器或实际的 RPC 调用
                super.start(responseListener, headers);
            }
        };
    }

}
