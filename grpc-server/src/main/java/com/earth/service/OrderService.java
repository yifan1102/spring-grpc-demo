package com.earth.service;


import com.earth.base.order.NewOrderReply;
import com.earth.base.order.NewOrderRequest;
import com.earth.base.order.OrderStatusEnum;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
public class OrderService extends com.earth.base.order.OrderServiceGrpc.OrderServiceImplBase {

    @Override
    public void createOrder(NewOrderRequest request, StreamObserver<NewOrderReply> responseObserver) {

        //写入WAL
        //丢入队列
        //上薄
        //撮合
        //成交
        NewOrderReply.Builder builder = NewOrderReply.newBuilder();
        builder.setCode(200);
        builder.setMessage("success");
        builder.setOrderId(123114141L);
        builder.setCrossAvailable("OK");
        builder.setStatus(OrderStatusEnum.NEW);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
