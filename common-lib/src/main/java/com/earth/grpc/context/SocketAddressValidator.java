/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.earth.grpc.context;

import com.google.errorprone.annotations.Immutable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Immutable
public interface SocketAddressValidator {

    /**
     * 用来判断地址是不是标准的 IP + Port 地址（InetSocketAddress）
     */
    SocketAddressValidator INET = new SocketAddressValidator() {
        @Override
        public boolean isValidSocketAddress(SocketAddress address) {
            return address instanceof InetSocketAddress;
        }
    };

    /**
     * 用来判断地址是不是 Unix Domain Socket 类型（Netty DomainSocketAddress 对象）
     * Unix Domain Socket（简称 UDS）是一种 本地进程间通信（IPC）机制
     */
    SocketAddressValidator UDS = new SocketAddressValidator() {
        @Override
        public boolean isValidSocketAddress(SocketAddress address) {
            return "DomainSocketAddress".equals(address.getClass().getSimpleName());
        }
    };

    boolean isValidSocketAddress(SocketAddress address);

}
