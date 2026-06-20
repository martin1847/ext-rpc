/**
 * Martin.Cong
 * Copyright (c) 2021-2021 All Rights Reserved.
 */
package tech.krpc.ext.runtime;

import java.util.Set;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

/**
 *
 * @author Martin.C
 * @version 2021/11/18 10:52 AM
 */
@ConfigGroup
public interface ServerApp {

    /**
     * 服务器地址,如 https://backoffice-api.botaoyx.com
     */
    String url();

    /**
     * 默认序列化方式 SerialEnum JSON,HESSIAN,KRYO
     */
    @WithDefault("JSON")
    String serial();

    /**
     * 服务扫描的package，多个英文逗号隔开,如 com.example.auth
     */
    Set<String> scan();


    default boolean isMatch(String rpcClass) {
        for (var s : scan()) {
            if (rpcClass.startsWith(s)) {
                return true;
            }
        }
        return false;
    }
}