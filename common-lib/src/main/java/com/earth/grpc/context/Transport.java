/*
 ************************************
 * @项目名称: btg-shard-lib
 * @文件名称: Transport
 * @Date 2018/07/20
 * @Author toyoda@bitgroup.com
 * @Copyright（C）: 2018 BitCloudGroup Inc.   All rights reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 **************************************
 */
package com.earth.grpc.context;

public enum Transport {

    NETTY_NIO(true, "The Netty Java NIO transport. Using this with TLS requires "
            + "that the Java bootclasspath be configured with Jetty ALPN boot.",
            SocketAddressValidator.INET),
    NETTY_EPOLL(true, "The Netty native EPOLL transport. Using this with TLS requires that "
            + "OpenSSL be installed and configured as described in "
            + "http://netty.io/wiki/forked-tomcat-native.html. Only supported on Linux.",
            SocketAddressValidator.INET),
    NETTY_UNIX_DOMAIN_SOCKET(false, "The Netty Unix Domain Socket transport. This currently "
            + "does not support TLS.",
            SocketAddressValidator.UDS);

    private final boolean tlsSupported;

    private final String description;

    private final SocketAddressValidator socketAddressValidator;

    Transport(boolean tlsSupported, String description,
              SocketAddressValidator socketAddressValidator) {
        this.tlsSupported = tlsSupported;
        this.description = description;
        this.socketAddressValidator = socketAddressValidator;
    }

}