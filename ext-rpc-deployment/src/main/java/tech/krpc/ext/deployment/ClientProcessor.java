/**
 * Martin.Cong
 * Copyright (c) 2021-2021 All Rights Reserved.
 */
package tech.krpc.ext.deployment;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import tech.krpc.client.RpcClientFactory;
import tech.krpc.ext.runtime.ClientConfig;
import tech.krpc.ext.runtime.ClientRecorder;
import tech.krpc.ext.runtime.ServerApp;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/**
 *
 * @author Martin.C
 * @version 2021/12/07 6:01 PM
 */
public interface ClientProcessor {

    Logger LOG = Logger.getLogger(ClientProcessor.class);

    static void genRpcClientFactorys(ClientConfig config,
                                     BuildProducer<RpcServiceMBI> clientServiceMBIS,
                                     BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
                                     ClientRecorder recorder, CombinedIndexBuildItem indexBuildItem) {

        if (config.apps().isEmpty()) {
            LOG.info("==== SKip genRpcClientFactorys ..");
            return;
        }

        // App @RpcService classes live in the application classloader, not this deployment/extension
        // classloader — resolve them via the augmentation TCCL, else Class.forName throws CNFE at
        // build time for a real client consumer (EXTRPC-URL-001; latent until a client app was
        // actually configured). initialize=false: we only need the Class for name-match + recording
        // it as a class literal into the runtime supplier.
        var appCl = Thread.currentThread().getContextClassLoader();
        var services = indexBuildItem.getIndex().getAnnotations(RpcProcessor.RPC_SERVICE)
                .stream().map(it -> {
                    try {
                        return Class.forName(it.target().asClass().name().toString(), false, appCl);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());

        //var proxySet = new HashSet<Class>();
        for (Entry<String, ServerApp> entry : config.apps().entrySet()) {
            String app = entry.getKey();
            ServerApp host = entry.getValue();

            var matched = services.stream().filter(it -> host.isMatch(it.getName())).collect(Collectors.toList());

            if (matched.isEmpty()) {
                LOG.info("=== Ignore Empty Service Found for : " + host);
                continue;
            }

            matched.forEach(s -> {
                clientServiceMBIS.produce(new RpcServiceMBI(s, app));
                //proxySet.add(s);
            });

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(RpcClientFactory.class)
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .unremovable()
                    //.destroyer(ClientDestory.class)
                    .supplier(recorder.clientFactorySupplier(app));

            configurator.defaultBean();
            configurator.addQualifier().annotation(Named.class).addValue("value", app).done();

            LOG.info("=== Set RpcClientFactory [ " + matched.size() + " -> " + app
                    + " ] (url runtime-resolved) : "
                    + matched.stream().map(Class::getSimpleName).collect(Collectors.joining(",")));
            syntheticBeanBuildItemBuildProducer.produce(configurator.done());
        }

        //if(proxySet.size()>0){
        //    var list = proxySet.stream().map(Class::getName).collect(Collectors.toList());
        //    proxy.produce(new NativeImageProxyDefinitionBuildItem(list));
        //    LOG.info("=== [ "+ proxySet.size() +" ClientProxy ] : " + proxySet.stream()
        //            .map(Class::getSimpleName).collect(Collectors.joining(",")));
        //}

    }

    static void genRpcClients(ClientRecorder recorder,
                              List<RpcServiceMBI> serviceMBIS,
                              BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {

        for (RpcServiceMBI i : serviceMBIS) {

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(i.getName())
                    .scope(ApplicationScoped.class)//.scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .supplier(recorder.rpcClientSupplier(i.getName(), i.getApp()));
            configurator.defaultBean();
            configurator.addQualifier().annotation(Named.class).addValue("value", i.getApp()).done();

            var clzName = i.getName().getName();
            //proxy.produce(new NativeImageProxyDefinitionBuildItem(clzName));
            LOG.info("CDI ClientProxy RpcService : " + clzName + " -> " + i.getApp());
            syntheticBeanBuildItemBuildProducer.produce(configurator.done());
        }

    }

}