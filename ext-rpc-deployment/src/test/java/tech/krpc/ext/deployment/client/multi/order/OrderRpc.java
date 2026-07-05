package tech.krpc.ext.deployment.client.multi.order;

import tech.krpc.annotation.RpcService;
import tech.krpc.model.RpcResult;

/**
 * Multi-client fixture for EXTRPC-URL-001 — the app left on its build-time default URL.
 * Its own scan package ({@code quarkus.rpc.client.orderapp.scan}) matches only this
 * {@code @RpcService}, so {@code genRpcClientFactorys} produces exactly one
 * {@code RpcClientFactory} qualified {@code @Named("orderapp")}.
 */
@RpcService
public interface OrderRpc {

    RpcResult<String> place(String sku);
}
