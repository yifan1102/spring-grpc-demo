package com.earth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "broker.proxy", havingValue = "false")
public class GrpcClientConfig {
}
