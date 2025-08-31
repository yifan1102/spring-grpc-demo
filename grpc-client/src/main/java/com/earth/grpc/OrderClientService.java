package com.earth.grpc;

import io.grpc.Channel;
import net.devh.boot.grpc.client.inject.GrpcClient;

public class OrderClientService {



    @GrpcClient("grpc-server")
    private Channel serverChannel;


    public String sendMessage(String jsonStr){


        return null;
    }
}
