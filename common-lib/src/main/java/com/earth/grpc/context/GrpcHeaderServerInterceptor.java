package com.earth.grpc.context;

import com.alibaba.fastjson.JSON;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
@Slf4j
public class GrpcHeaderServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        // 从 headers 中获取元数据
        Metadata.Key<String> key = Metadata.Key.of("grpc-header", Metadata.ASCII_STRING_MARSHALLER);
        String headerValue = headers.get(key);
        if (!ObjectUtils.isEmpty(headerValue) && !headerValue.isEmpty()) {
            GrpcHeader grpcHeader = JSON.parseObject(headerValue, GrpcHeader.class);
            if (!ObjectUtils.isEmpty(grpcHeader)) {
                Context newContext = Context.current().withValue(GrpcHeaderContext.key, grpcHeader);
                return Contexts.interceptCall(newContext, call, headers, next);
            }
        }
        return next.startCall(call, headers);
    }

}
