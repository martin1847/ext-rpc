package tech.krpc.ext.it.noclient.remote;

import tech.krpc.annotation.RpcService;
import tech.krpc.model.RpcResult;

/**
 * A CLIENT-side @RpcService with no local implementation, in the package named by
 * {@code quarkus.rpc.client.guardapp.scan} in application.properties.
 *
 * <p>This is what makes the guard strict rather than decorative: a client app IS configured and it
 * DOES match a service, so if the client BuildSteps were reachable they would fire and emit a
 * synthetic RpcClientFactory + proxy bean. The only thing stopping them is
 * {@code onlyIf = RpcProcessor.IsClient}, which is false because rpc-client is off the classpath.
 */
@RpcService(description = "no-client guard: would-be client service")
public interface RemoteService {

    RpcResult<String> remoteEcho(String payload);
}
