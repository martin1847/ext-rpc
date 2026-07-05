package tech.krpc.ext.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import tech.krpc.annotation.Doc;
import tech.krpc.annotation.RpcService;
import tech.krpc.ext.runtime.ClientConfig;
import tech.krpc.ext.runtime.ClientRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.jboss.logging.Logger;
import tech.krpc.model.RpcResult;

public class RpcProcessor {

    static final Logger LOG     = Logger.getLogger(RpcProcessor.class);
    static final String FEATURE = "ext-rpc";

    static final DotName RPC_SERVICE = DotName.createSimple(RpcService.class.getName());

    static final DotName RPC_RESULT = DotName.createSimple(RpcResult.class.getName());

    static final         DotName          DOC_ANNO = DotName.createSimple(Doc.class.getName());
    private static final org.slf4j.Logger log      = LoggerFactory.getLogger(RpcProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Fully qualified names of the io.grpc SPI provider impls whose no-arg constructors must be
     * reflectively available so grpc can instantiate them in a native image (Quarkus builds with
     * {@code -H:-UseServiceLoaderFeature}). Mirrors the proven downstream
     * {@code NativeGrpcProviderConfig} @RegisterForReflection holder (krpc SPEC.md §13.2), now
     * upstreamed so consumers need zero per-service glue.
     */
    static final String[] GRPC_PROVIDER_IMPLS = {
            "io.grpc.netty.NettyServerProvider",
            "io.grpc.netty.NettyChannelProvider",
            "io.grpc.netty.UdsNettyChannelProvider",
            "io.grpc.internal.PickFirstLoadBalancerProvider",
            "io.grpc.internal.DnsNameResolverProvider"
    };

    /**
     * Server-side native support for krpc's raw io.grpc usage. Only emitted for a server build
     * (a client-only consumer's provider lists are already baked by krpc's client
     * {@code GraalvmBuild}). Two parts:
     * <ul>
     *   <li>reflective registration of the grpc provider impls (no-arg constructors) so grpc's
     *       runtime ServiceLoader can instantiate them;</li>
     *   <li>a build-time {@link tech.krpc.ext.runtime.graal.NettyServerProviderFeature} that forces
     *       {@code io.grpc.ServerProvider.provider()} during analysis, baking the server provider
     *       list into the image heap (equivalent of the downstream {@code --features=} flag).</li>
     * </ul>
     */
    @BuildStep(onlyIf = IsServer.class)
    void regGrpcServerProvidersForNative(BuildProducer<ReflectiveClassBuildItem> reflective,
                                         BuildProducer<NativeImageFeatureBuildItem> feature) {
        reflective.produce(ReflectiveClassBuildItem.builder(GRPC_PROVIDER_IMPLS)
                .constructors(true).methods(false).fields(false).build());
        feature.produce(new NativeImageFeatureBuildItem(
                "tech.krpc.ext.runtime.graal.NettyServerProviderFeature"));
        LOG.info("=== [ ext-rpc native ] registered " + GRPC_PROVIDER_IMPLS.length
                + " grpc provider impls + ServerProvider feature");
    }

    @BuildStep
    void regRpcServiceForNative(ClientConfig config,
                                BuildProducer<ReflectiveClassBuildItem> reflective,
                                BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
                                CombinedIndexBuildItem indexBuildItem) {

        boolean serverExists = isServer();
        var dtoSet = new HashSet<String>();
        var annotationSetForMetaData = new HashSet<DotName>();

        if (serverExists) {
            annotationSetForMetaData.add(DOC_ANNO);
            var refInfo = ReflectiveClassBuildItem.builder(DOC_ANNO.toString()).methods(true).fields(false).build();
            //new ReflectiveClassBuildItem(true, false, DOC_ANNO.toString())
            reflective.produce(refInfo);
            for (AnnotationInstance i : indexBuildItem.getIndex().getAnnotations(DOC_ANNO)) {
                if (i.target().kind() == AnnotationTarget.Kind.FIELD) {
                    i.target().asField().annotations()
                            .stream().map(AnnotationInstance::name)
                            .forEach(annotationSetForMetaData::add);
                }
            }
        }

        var serverList = new ArrayList<DotName>();
        for (AnnotationInstance i : indexBuildItem.getIndex().getAnnotations(RPC_SERVICE)) {
            var cls = i.target().asClass();
            //var dotName = cls.name().toString();
            serverList.add(cls.name());

            var methods = cls.methods();
            var thisSet = new HashSet<DotName>();
            for (var m : methods) {
                if (serverExists) {
                    m.annotations().forEach(it -> annotationSetForMetaData.add(it.name()));
                }
                recursionParameterizedType(thisSet, m.returnType());
                m.parameters().forEach(it -> recursionParameterizedType(thisSet, it.type()));
                //addRefDtoClass(dtoSet, thisSet);

                thisSet.stream().map(DotName::toString)
                        .filter(c -> !c.startsWith("java."))
                        .forEach(dtoSet::add);
            }
        }

        if (serverList.size() > 0) {
            var clientClsList = new ArrayList<DotName>();
            boolean clientExists = new IsClient().getAsBoolean();
            if (clientExists && config != null && config.apps() != null && config.apps().size() > 0) {
                config.apps().values().forEach(host ->
                        serverList.forEach(s -> {
                            var clz = s.toString();
                            if (host.isMatch(clz)) {
                                clientClsList.add(s);
                                // 貌似代理批量执行有bug
                                // UnsupportedFeatureError: Proxy class defined by interfaces [interface CaptchaService] not found.
                                // Generating proxy classes at runtime is not supported.
                                proxy.produce(new NativeImageProxyDefinitionBuildItem(clz));
                            }
                        })
                );
            }

            if (clientClsList.size() > 0) {
                LOG.info("=== [ " + clientClsList.size() + " RpcClient  ]  : " +
                        clientClsList.stream().map(DotName::withoutPackagePrefix).collect(Collectors.joining(",")));

                // 默认client和server不共存
                serverList.removeAll(clientClsList);
            }

            if (serverExists && serverList.size() > 0) {
                var array = serverList.stream().map(DotName::toString).toArray(String[]::new);
                var refInfo = ReflectiveClassBuildItem.builder(array).methods(true).fields(false).build();
                //new ReflectiveClassBuildItem(true, false, array)
                reflective.produce(refInfo);
                LOG.info("=== [ " + serverList.size() + " RpcService  ]  : " +
                        serverList.stream().map(DotName::withoutPackagePrefix).collect(Collectors.joining(",")));
            }

        }

        LOG.info("=== [ " + annotationSetForMetaData.size() + " Annotation ] For MetaDataService : " +
                annotationSetForMetaData.stream().map(DotName::withoutPackagePrefix).collect(Collectors.joining(","))
        );
        if (annotationSetForMetaData.size() > 0) {
            var refInfo = ReflectiveClassBuildItem
                    .builder(annotationSetForMetaData.stream().map(DotName::toString).toArray(String[]::new))
                    .methods(true)
                    .fields(false).build();
            reflective.produce(refInfo);
        }

        LOG.info("=== [ " + dtoSet.size() + " DTO ] For Jackson : " + dtoSet.stream()
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .collect(Collectors.joining(","))
        );
        if (!dtoSet.isEmpty()) {
            var refInfo = ReflectiveClassBuildItem.builder(dtoSet.toArray(new String[0])).methods(true).fields(true).build();
            reflective.produce(refInfo);

            //var childSet = recursionNestDtoType(dtoSet);

            var totalChilds = new HashSet<String>(16);

            Set<String> childSet = dtoSet;
            int max_level = 8;
            int i = 1;
            for (; i <= max_level; i++) {
                childSet = recursionNestDtoType(indexBuildItem.getIndex(), childSet);
                if (!childSet.isEmpty()) {
                    LOG.info("====== Level " + i + " [ " + childSet.size() + " Nest DTO ] For Jackson : " + childSet.stream()
                            .map(name -> name.substring(name.lastIndexOf('.') + 1))
                            .collect(Collectors.joining(","))
                    );
                    totalChilds.addAll(childSet);
                } else {
                    break;
                }
            }

            if (!totalChilds.isEmpty()) {
                var childInfo = ReflectiveClassBuildItem.builder(totalChilds.toArray(new String[0])).methods(true).fields(true).build();
                reflective.produce(childInfo);
                LOG.info("=== [ " + totalChilds.size() + " Nest DTO ] For Jackson : " + totalChilds.stream()
                        .map(name -> name.substring(name.lastIndexOf('.') + 1))
                        .collect(Collectors.joining(","))
                );
                if (i == max_level) {
                    log.warn("===== TOO DEEP Nest Dto !!! Max LEVEL = " + i + " =====");
                }
            }
        }

    }

    /**
     * Walk each DTO plus its super-class chain via Jandex (classloader-free) and collect the
     * nested/inherited field types that also need reflective registration. Replaces the previous
     * {@code Class.forName}-based walk, which threw {@link ClassNotFoundException} in the Quarkus
     * native augmentation phase (the augmentation classloader can't see application DTOs) and thus
     * silently skipped registration of a DTO's inherited field types.
     */
    static Set<String> recursionNestDtoType(IndexView index, Set<String> total) {
        var childSet = new HashSet<String>();
        for (var dtoName : total) {
            var seen = new HashSet<DotName>();
            var current = index.getClassByName(DotName.createSimple(dtoName));
            // Non-application DTOs may be absent from the index -> nothing to walk.
            while (current != null
                    && !DotName.OBJECT_NAME.equals(current.name())
                    && seen.add(current.name())) {
                for (var f : current.fields()) {
                    collectDtoType(index, childSet, total, f.type());
                }
                var superType = current.superClassType();
                if (superType != null) {
                    // Also captures a generic base's actual type args, e.g. Base<Foo> -> Foo,
                    // which the old raw getSuperclass() walk lost.
                    collectDtoType(index, childSet, total, superType);
                }
                var superName = current.superName();
                if (superName == null || DotName.OBJECT_NAME.equals(superName)) {
                    break;
                }
                var next = index.getClassByName(superName);
                if (next == null) {
                    // Base class is outside the Jandex index (typically an external library type):
                    // the walk stops here, so this base's own declared fields can't be collected.
                    // Surface it instead of failing silently.
                    log.warn(
                            "[krpc-ext] DTO {} extends non-indexed base {}; its inherited fields are"
                                    + " NOT auto-registered for native reflection — add"
                                    + " @RegisterForReflection to {} if it is used at native runtime.",
                            current.name(), superName, superName);
                    break;
                }
                current = next;
            }
        }
        return childSet;
    }

    static void collectDtoType(IndexView index, Set<String> childSet, Set<String> total, Type type) {
        switch (type.kind()) {
            case CLASS -> addNestedDto(index, childSet, total, type.name());
            case PARAMETERIZED_TYPE -> {
                var pt = type.asParameterizedType();
                addNestedDto(index, childSet, total, pt.name());
                for (var arg : pt.arguments()) {
                    collectDtoType(index, childSet, total, arg);
                }
            }
            default -> {
                // PRIMITIVE / ARRAY / VOID / TYPE_VARIABLE / WILDCARD_TYPE: skip.
                // Matches the original reflection walk (arrays -> use List, primitives ignored).
            }
        }
    }

    private static void addNestedDto(IndexView index, Set<String> childSet, Set<String> total, DotName name) {
        var fName = name.toString();
        if (fName.startsWith("java.") || total.contains(fName) || childSet.contains(fName)) {
            return;
        }
        var ci = index.getClassByName(name);
        // Skip enums/interfaces/annotations when the index can prove it; unknown (non-indexed)
        // types are still registered rather than silently dropped.
        if (ci != null && (ci.isEnum() || ci.isInterface() || ci.isAnnotation())) {
            return;
        }
        childSet.add(fName);
    }

    static class IsClient implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            return checkExists("tech.krpc.client.ClientContext");
        }
    }

    static class IsServer implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            return isServer();
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = IsClient.class)
    void genRpcClientFactorys(ClientConfig config,
                              BuildProducer<RpcServiceMBI> clientServiceMBIS,
                              BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
                              ClientRecorder recorder, CombinedIndexBuildItem indexBuildItem) throws Exception {
        ClientProcessor.genRpcClientFactorys(config, clientServiceMBIS,
                syntheticBeanBuildItemBuildProducer, recorder, indexBuildItem);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = IsClient.class)
    void genRpcClients(ClientRecorder recorder,
                       List<RpcServiceMBI> serviceMBIS,

                       BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {

        ClientProcessor.genRpcClients(recorder, serviceMBIS, syntheticBeanBuildItemBuildProducer);
    }

    static boolean isServer() {
        return checkExists("tech.krpc.server.ServerContext");
    }

    static boolean checkExists(String client) {
        try {
            Class.forName(client);
            return true;
        } catch (ClassNotFoundException e) {
            LOG.warn("=== IGNORE NotFound RpcProcessor : " + client);
        }
        return false;

    }

    static void recursionParameterizedType(Set<DotName> total, Type t) {
        if (t.kind() == Kind.CLASS) {
            total.add(t.name());
        } else if (t.kind() == Kind.PARAMETERIZED_TYPE) {
            var pt = t.asParameterizedType();
            if (!RPC_RESULT.equals(pt.name())) {
                total.add(t.name());
            }
            for (var s : pt.arguments()) {
                recursionParameterizedType(total, s);
            }
        }
    }

}
