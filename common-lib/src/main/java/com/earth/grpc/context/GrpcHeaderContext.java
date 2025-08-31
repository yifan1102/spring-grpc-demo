package com.earth.grpc.context;

import io.grpc.Context;
import org.slf4j.MDC;
import org.springframework.util.ObjectUtils;

public class GrpcHeaderContext {

    private static final ThreadLocal<GrpcHeader> GRPC_HEADER_THREAD_LOCAL = new ThreadLocal<>();
    public static final Context.Key<GrpcHeader> key = Context.key("grpc-header");

    public static void setHeader(GrpcHeader grpcHeader) {
        GRPC_HEADER_THREAD_LOCAL.set(grpcHeader);
        if (!ObjectUtils.isEmpty(grpcHeader)) {
            MDC.put("userId", String.valueOf(grpcHeader.getUserId()));
            MDC.put("orgId", String.valueOf(grpcHeader.getOrgId()));
            MDC.put("traceId", String.valueOf(grpcHeader.getRequestId()));
        }
    }

    public static GrpcHeader getHeader() {
        GrpcHeader grpcHeader = null;
        if (!ObjectUtils.isEmpty(GRPC_HEADER_THREAD_LOCAL.get())) {
            grpcHeader = GRPC_HEADER_THREAD_LOCAL.get();
        }
        if (!ObjectUtils.isEmpty(key.get())) {
            grpcHeader = key.get();
        }
        if (!ObjectUtils.isEmpty(grpcHeader)) {
            MDC.put("userId", String.valueOf(grpcHeader.getUserId()));
            MDC.put("orgId", String.valueOf(grpcHeader.getOrgId()));
            MDC.put("traceId", String.valueOf(grpcHeader.getRequestId()));
        }
        return grpcHeader;
    }

    public static void cleanHeader() {
        GRPC_HEADER_THREAD_LOCAL.remove();
        MDC.remove("userId");
        MDC.remove("orgId");
        MDC.remove("traceId");
    }

}
