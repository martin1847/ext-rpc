package tech.krpc.ext.deployment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import io.grpc.ManagedChannel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.krpc.client.RpcClientFactory;

/**
 * EXTRPC-URL-001 compatibility guard — build-time pinned URL stays the effective dial target
 * when no runtime override is present. Protects the existing "pin the URL in application.properties"
 * behaviour so the fix (url → RUN_TIME root) keeps the build-time value as the fallback default.
 *
 * <p>GREEN before AND after the fix.
 */
class ClientUrlBuildTimeFallbackTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(EchoRpc.class))
            .overrideConfigKey("platform.quarkus.native.builder-image", "n/a-jvm-test")
            .overrideConfigKey("quarkus.rpc.client.testapp.url", "http://127.0.0.1:59991")
            .overrideConfigKey("quarkus.rpc.client.testapp.scan", "tech.krpc.ext.deployment.client");

    @Inject
    @Named("testapp")
    RpcClientFactory factory;

    @Test
    void buildTimeUrlIsFallbackWhenNoRuntimeOverride() throws Exception {
        assertEquals("127.0.0.1:59991", dialTarget(factory),
                "with no runtime override, the build-time URL must remain the dial target");
    }

    static String dialTarget(RpcClientFactory f) throws Exception {
        RpcClientFactory real = ClientProxy.unwrap(f);
        Field field = RpcClientFactory.class.getDeclaredField("channel");
        field.setAccessible(true);
        return ((ManagedChannel) field.get(real)).authority();
    }
}
