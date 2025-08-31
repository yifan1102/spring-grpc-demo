package com.earth.controller;

import com.earth.dto.MarketOrder;
import com.earth.grpc.OrderClientService;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class OrderController {


    private OrderClientService orderClientService;

    @PostMapping("/create/order")
    public String  createOrder(@RequestBody MarketOrder order) {

        orderClientService.sendMessage(null);
        log.info("");

        return order.toString();
    }
}
