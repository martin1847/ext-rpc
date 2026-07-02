package tech.krpc.ext.runtime.graal;

import org.graalvm.nativeimage.hosted.Feature;

/**
 * Native (GraalVM) build-time registration of the grpc <em>server-side</em> provider.
 *
 * <p>krpc talks to io.grpc directly (not via quarkus-grpc). Quarkus builds native images with
 * {@code -H:-UseServiceLoaderFeature}, so grpc's {@code ServiceLoader} lookups find nothing unless
 * the provider lists are materialized at build time. krpc's own client feature
 * ({@code tech.krpc.client.ext.GraalvmBuild}) eagerly touches the <em>client</em> registries
 * ({@code ManagedChannelProvider} / {@code NameResolverRegistry} / {@code LoadBalancerRegistry}),
 * baking their provider lists into the image heap. Nothing touches the <em>server</em> side, so
 * {@code io.grpc.ServerRegistry} is empty in the image and the native server dies at boot with
 * {@code ManagedChannelProvider$ProviderNotFoundException: No functional server found}.
 *
 * <p>This feature mirrors the client pattern for the server side: {@code beforeAnalysis} forces
 * {@code io.grpc.ServerProvider.provider()} during analysis, so {@code ServerRegistry}'s hard-coded
 * candidate {@code NettyServerProvider} is discovered (its impl class is registered for reflection
 * by {@code ext-rpc-deployment}'s {@code RpcProcessor}) and its provider list is captured into the
 * image heap.
 *
 * <p>Wired automatically for consumers via {@code ext-rpc-deployment} emitting a
 * {@code NativeImageFeatureBuildItem} for this class — no per-service {@code --features=} flag.
 * The old per-service workaround this replaces was the downstream
 * {@code tech.luohan.ledger.server.NettyServerProviderFeature} (krpc SPEC.md §13.2).
 */
public final class NettyServerProviderFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // Eagerly initialize the server registry's provider list at build time so it is baked
        // into the image heap (Quarkus disables the ServiceLoader feature for native builds).
        io.grpc.ServerProvider.provider();
    }

    @Override
    public String getDescription() {
        return "Registers io.grpc server-side provider (NettyServerProvider) for native image";
    }
}
