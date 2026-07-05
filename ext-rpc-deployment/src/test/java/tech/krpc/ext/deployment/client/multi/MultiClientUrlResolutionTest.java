package tech.krpc.ext.deployment.client.multi;

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
import tech.krpc.ext.deployment.client.multi.order.OrderRpc;
import tech.krpc.ext.deployment.client.multi.payment.PaymentRpc;

/**
 * EXTRPC-URL-001 multi-client guard — mirrors the 8-service consumer shape.
 *
 * <p>Two client apps configured in one deployment: {@code paymentapp} and {@code orderapp}.
 * Both pin a build-time placeholder URL in {@code application.properties}. Only {@code paymentapp}
 * gets a runtime override (ordinal 399 &gt; app.properties 250), {@code orderapp} is left on its
 * build-time default. The synthetic per-app {@code RpcClientFactory} channels MUST resolve
 * independently — the runtime override applies to {@code paymentapp} only, {@code orderapp} keeps
 * its build-time value, and neither leaks into the other.
 *
 * <p>Guards against a per-app URL mixup / global-override regression in the RUN_TIME resolution
 * path (each factory reads {@code runtimeConfig.apps().get(appName)} by its own name).
 */
class MultiClientUrlResolutionTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(PaymentRpc.class, OrderRpc.class))
            // No platform BOM on the extension test classpath — define the native builder-image
            // expression so config mapping validates (JVM test, value irrelevant).
            .overrideConfigKey("platform.quarkus.native.builder-image", "n/a-jvm-test")
            // paymentapp: build-time placeholder + runtime override (override must win)
            .overrideConfigKey("quarkus.rpc.client.paymentapp.url", "http://127.0.0.1:59991")
            .overrideConfigKey("quarkus.rpc.client.paymentapp.scan",
                    "tech.krpc.ext.deployment.client.multi.payment")
            .overrideRuntimeConfigKey("quarkus.rpc.client.paymentapp.url", "http://10.20.30.40:50057")
            // orderapp: build-time placeholder only, NO runtime override (default must hold)
            .overrideConfigKey("quarkus.rpc.client.orderapp.url", "http://127.0.0.1:59992")
            .overrideConfigKey("quarkus.rpc.client.orderapp.scan",
                    "tech.krpc.ext.deployment.client.multi.order");

    @Inject
    @Named("paymentapp")
    RpcClientFactory paymentFactory;

    @Inject
    @Named("orderapp")
    RpcClientFactory orderFactory;

    @Test
    void eachAppResolvesItsOwnUrlWithNoCrossAppMixup() throws Exception {
        assertEquals("10.20.30.40:50057", dialTarget(paymentFactory),
                "paymentapp must dial its RUNTIME override, not the build-time placeholder");
        assertEquals("127.0.0.1:59992", dialTarget(orderFactory),
                "orderapp (no runtime override) must keep its build-time default, "
                        + "unaffected by paymentapp's override");
    }

    static String dialTarget(RpcClientFactory f) throws Exception {
        RpcClientFactory real = ClientProxy.unwrap(f);
        Field field = RpcClientFactory.class.getDeclaredField("channel");
        field.setAccessible(true);
        return ((ManagedChannel) field.get(real)).authority();
    }
}
