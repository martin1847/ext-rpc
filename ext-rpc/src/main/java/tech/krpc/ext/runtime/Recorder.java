package tech.krpc.ext.runtime;

import java.util.function.Supplier;

import tech.krpc.client.RpcClientFactory;

@io.quarkus.runtime.annotations.Recorder
public interface Recorder {

    Supplier<RpcClientFactory> clientFactorySupplier(String appName);

    Supplier<Object> rpcClientSupplier(Class rpcService, String app);
}




