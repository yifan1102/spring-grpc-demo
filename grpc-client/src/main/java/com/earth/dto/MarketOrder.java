package com.earth.dto;

import com.google.type.DateTime;
import lombok.Data;

@Data
public class MarketOrder {

    private String orderId;
    private String orderType;
    private String orderPrice;
    private String orderAmount;
    private String orderStatus;
}
