package tech.krpc.ext.deployment.client;

import tech.krpc.annotation.RpcService;
import tech.krpc.model.RpcResult;

/**
 * Fixture for EXTRPC-URL-001 guard tests. A single {@code @RpcService} whose package matches
 * {@code quarkus.rpc.client.testapp.scan}, so the client build step ({@code genRpcClientFactorys})
 * produces exactly one synthetic {@code RpcClientFactory} qualified {@code @Named("testapp")}.
 * The factory's gRPC channel dial target is what the guards assert against.
 */
@RpcService
public interface EchoRpc {

    RpcResult<String> echo(String msg);
}
