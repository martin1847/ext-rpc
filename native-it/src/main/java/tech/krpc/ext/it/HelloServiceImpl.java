package tech.krpc.ext.it;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import tech.krpc.model.RpcResult;

/**
 * Container wiring (SPEC §10): {@code @ApplicationScoped @Startup}. Quarkus discovers the
 * bean, sees it implements an {@code @RpcService} interface, and exposes it on the gRPC +
 * HTTP gateway port (default 50051).
 */
@ApplicationScoped
@Startup
public class HelloServiceImpl implements HelloService {

    @Override
    public RpcResult<HelloReply> hello(HelloRequest req) {
        return RpcResult.ok(new HelloReply(
                "Hello, " + req.getName() + "!",
                System.currentTimeMillis()));
    }
}
