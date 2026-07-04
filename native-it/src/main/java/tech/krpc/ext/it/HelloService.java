package tech.krpc.ext.it;

import tech.krpc.annotation.Doc;
import tech.krpc.annotation.RpcService;
import tech.krpc.annotation.UnsafeWeb;
import tech.krpc.model.RpcResult;

/**
 * Baseline flat-DTO service (NATIVE-002 guard): proves the native image boots, the grpc
 * server provider is registered, and unary gRPC dispatch works — independent of the
 * inheritance path. Call path {@code native-it/Hello/hello}.
 *
 * <p>{@code @UnsafeWeb} is REQUIRED even for a raw gRPC (rpcurl) caller: without it the
 * service name is hidden with a {@code -} prefix (RefUtils.HIDDEN_SERVICE), so the
 * dispatch path becomes {@code -native-it/Hello/...} instead of {@code native-it/Hello/...}.
 */
@UnsafeWeb
@RpcService(description = "ext-rpc native-IT baseline flat-DTO service")
public interface HelloService {

    @Doc("Returns a greeting for the given name.")
    RpcResult<HelloReply> hello(HelloRequest req);
}
