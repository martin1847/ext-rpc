package tech.krpc.ext.it.client;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;
import tech.krpc.client.RpcClientFactory;

/**
 * EXTRPC-URL-001 native env-override guard — forces the (lazy, runtime-init) payment-server
 * {@link RpcClientFactory} to be instantiated at boot, so the recorder emits its runtime-resolved
 * dial target into the boot log. The native-smoke step asserts that target is the ENV override,
 * not the build-time placeholder — proving runtime URL override now works in a native image.
 *
 * <p>Touches a no-arg method on the factory (no network I/O — gRPC channels connect lazily) purely
 * to trigger the ApplicationScoped contextual-instance creation.
 */
@ApplicationScoped
@Startup
public class ClientUrlProbe {

    private static final Logger LOG = Logger.getLogger(ClientUrlProbe.class);

    @Inject
    @Named("payment-server")
    RpcClientFactory paymentFactory;

    @PostConstruct
    void probe() {
        // Force the client proxy to resolve its contextual instance → runs the recorder supplier,
        // which logs "=== RpcClientFactory [ payment-server -> <host>:<port> ] (runtime-resolved)".
        paymentFactory.getDefaultSerial();
        LOG.info("EXTRPC-URL-001 probe: payment-server RpcClientFactory instantiated at runtime");
    }
}
