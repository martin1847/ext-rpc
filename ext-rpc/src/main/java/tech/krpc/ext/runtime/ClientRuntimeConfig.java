package tech.krpc.ext.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

/**
 * Runtime half of the rpc-client config: the per-server-app dial URL (EXTRPC-URL-001).
 *
 * <p>Split out of {@link ClientConfig} (which stays {@code BUILD_AND_RUN_TIME_FIXED} for the
 * build-time concerns: {@code scan}/{@code serial}/{@code filters}) so that {@code url} is a true
 * {@code RUN_TIME} value. Same prefix and app key as {@link ClientConfig}
 * ({@code quarkus.rpc.client.<app>.url}), so a build-time {@code application.properties} URL stays
 * the runtime default and an env/system-property override wins by MicroProfile config ordinal —
 * which is exactly what native images could not do while the URL was consumed at build time.
 *
 * <p>Mirrors the Quarkus datasource pattern: one config prefix carried by two roots, a build-time
 * one and a runtime one.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.rpc.client")
public interface ClientRuntimeConfig {

    /**
     * 服务地址相关配置（运行时）。key = server-app 名，与 {@link ClientConfig#apps()} 对齐。
     */
    @ConfigDocSection
    @ConfigDocMapKey("server-app-name")
    @WithParentName
    Map<String, ClientHost> apps();

    interface ClientHost {

        /**
         * 服务器地址,如 https://backoffice-api.botaoyx.com。
         * 运行时值:env / 系统属性可覆盖构建期 application.properties 中的默认值。
         */
        String url();
    }
}
