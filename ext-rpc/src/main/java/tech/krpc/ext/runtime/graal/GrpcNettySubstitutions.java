package tech.krpc.ext.runtime.graal;

import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * GraalVM substitution disabling netty's epoll transport for grpc's {@code io.grpc.netty.Utils}
 * (native-image cannot statically initialize the epoll native library detection).
 *
 * <p><b>Gated (NATIVE-002).</b> Quarkus's {@code quarkus-grpc-common} ships a byte-identical
 * authoritative substitution of the same target
 * ({@code io.quarkus.grpc.common.runtime.graal.Target_io_grpc_netty_Utils}:
 * {@code isEpollAvailable()->false}, {@code getEpollUnavailabilityCause()->null}). When a consumer
 * pulls grpc-common (e.g. transitively via quarkus-opentelemetry -> vertx-grpc), two substitutions
 * of the same class abort the native build ("conflicts with previously registered"), which forced
 * every consumer to strip this class via {@code quarkus.class-loading.removed-resources}
 * (krpc SPEC.md §13.3). This substitution now activates ONLY when Quarkus's version is absent —
 * i.e. a krpc consumer that uses raw io.grpc without grpc-common (e.g. krpc's own test-server) —
 * so it stays load-bearing there while never colliding when grpc-common is present. Consumers drop
 * the {@code removed-resources} strip.
 *
 * <p>The former {@code static { ServerProvider.provider(); }} block that lived here (server-side
 * registration) is gone: that responsibility moved to
 * {@link NettyServerProviderFeature}, which ext-rpc-deployment wires for every server build, so
 * server registration no longer depends on this class being included in the image.
 */
@TargetClass(className = "io.grpc.netty.Utils", onlyWith = GrpcNettySubstitutions.QuarkusGrpcCommonAbsent.class)
final class Target_io_grpc_netty_Utils {

    @Substitute
    static boolean isEpollAvailable() {
        return false;
    }

    @Substitute
    private static Throwable getEpollUnavailabilityCause() {
        return null;
    }
}

@SuppressWarnings("unused")
class GrpcNettySubstitutions {

    /**
     * Build-time predicate: true when Quarkus's authoritative grpc-common substitution of
     * {@code io.grpc.netty.Utils} is NOT on the classpath, so ext-rpc's substitution is the only
     * one and must apply. When grpc-common is present, Quarkus's version wins and ext-rpc's is
     * disabled to avoid a duplicate-substitution abort.
     */
    static final class QuarkusGrpcCommonAbsent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            try {
                getClass().getClassLoader()
                        .loadClass("io.quarkus.grpc.common.runtime.graal.Target_io_grpc_netty_Utils");
                return false; // Quarkus substitution present -> defer to it
            } catch (Throwable notFound) {
                return true; // absent -> ext-rpc must provide the substitution
            }
        }
    }
}
