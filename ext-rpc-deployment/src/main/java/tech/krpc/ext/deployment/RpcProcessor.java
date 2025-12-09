package tech.krpc.ext.deployment;

import java.lang.reflect.ParameterizedType;
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
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
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
            if (clientExists && config != null && config.apps != null && config.apps.size() > 0) {
                config.apps.values().forEach(host ->
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
                childSet = recursionNestDtoType(childSet);
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

    static Set<String> recursionNestDtoType(Set<String> total) {
        var childSet = new HashSet<String>();
        for (var dtoName : total) {

            try {
                var base = Class.forName(dtoName);
                do {
                    extractSuper(childSet, total, base);
                    base = base.getSuperclass();
                    recursionNestDtoType(childSet, total, base);
                } while (base != Object.class);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return childSet;
    }

    private static void extractSuper(HashSet<String> childSet, Set<String> total, Class clz) {
        //if(null == clz){
        //    LOG.debug("skip null clz");
        //    return;
        //}
        for (var f : clz.getDeclaredFields()) {
            var type = f.getGenericType();
            recursionNestDtoType(childSet, total, type);
        }
    }

    static void recursionNestDtoType(HashSet<String> childSet, Set<String> total, java.lang.reflect.Type type) {
        if (type instanceof Class) {
            var fClz = ((Class<?>) type);
            var fName = fClz.getName();
            // skip Array , use list
            if (!fClz.isPrimitive()
                    && !fClz.isArray()
                    && !fClz.isEnum()
                    && !fClz.isInterface()
                    && !fName.startsWith("java.")
                    && !total.contains(fName)) {
                childSet.add(fName);
            }
        } else if (type instanceof ParameterizedType) {
            var pt = (ParameterizedType) type;
            recursionNestDtoType(childSet, total, pt.getRawType());
            for (var t : pt.getActualTypeArguments()) {
                recursionNestDtoType(childSet, total, t);
            }
        }
    }

    static class IsClient implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            return checkExists("tech.krpc.client.ClientContext");
        }
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(onlyIf = IsClient.class)
    void genRpcClientFactorys(ClientConfig config, BuildProducer<RpcServiceMBI> clientServiceMBIS,
                              BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
                              //BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
                              ClientRecorder recorder, CombinedIndexBuildItem indexBuildItem) throws Exception {
        ClientProcessor.genRpcClientFactorys(config, clientServiceMBIS, syntheticBeanBuildItemBuildProducer, recorder, indexBuildItem);
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
