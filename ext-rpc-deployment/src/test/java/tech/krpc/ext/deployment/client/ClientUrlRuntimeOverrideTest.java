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
 * EXTRPC-URL-001 guard — the LH regression, reproduced in-JVM.
 *
 * <p>Build-time property pins {@code testapp.url} to a dev placeholder (127.0.0.1:59991),
 * exactly like order-server's {@code application.properties}. A runtime-only override
 * ({@code overrideRuntimeConfigKey}, ordinal 399 &gt; env 300 &gt; app.properties 250) then
 * supplies the "pod env" value (10.20.30.40:50057). The synthetic {@code RpcClientFactory}'s
 * gRPC channel dial target MUST reflect the runtime value.
 *
 * <p>RED on current code: {@code quarkus.rpc.client} is {@code BUILD_AND_RUN_TIME_FIXED} and the
 * URL is consumed at a {@code STATIC_INIT} build step, so the channel is baked with the build-time
 * placeholder and the env override is ignored — the exact staging symptom.
 * GREEN after EXTRPC-URL-001 fix (url → RUN_TIME root, factory/channel → RUNTIME_INIT).
 */
class ClientUrlRuntimeOverrideTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(EchoRpc.class))
            // No platform BOM on the extension test classpath, so quarkus.native.builder-image's
            // default expression ${platform.quarkus.native.builder-image} has nothing to expand;
            // define it (JVM test, value irrelevant) so config mapping validates.
            .overrideConfigKey("platform.quarkus.native.builder-image", "n/a-jvm-test")
            // build-time placeholder (dev) — what gets pinned into application.properties
            .overrideConfigKey("quarkus.rpc.client.testapp.url", "http://127.0.0.1:59991")
            .overrideConfigKey("quarkus.rpc.client.testapp.scan", "tech.krpc.ext.deployment.client")
            // runtime "pod env" override — must win at dial time
            .overrideRuntimeConfigKey("quarkus.rpc.client.testapp.url", "http://10.20.30.40:50057");

    @Inject
    @Named("testapp")
    RpcClientFactory factory;

    @Test
    void runtimeEnvOverrideWinsAtDialTime() throws Exception {
        assertEquals("10.20.30.40:50057", dialTarget(factory),
                "client channel must dial the RUNTIME URL, not the build-time placeholder");
    }

    static String dialTarget(RpcClientFactory f) throws Exception {
        RpcClientFactory real = ClientProxy.unwrap(f);
        Field field = RpcClientFactory.class.getDeclaredField("channel");
        field.setAccessible(true);
        return ((ManagedChannel) field.get(real)).authority();
    }
}
