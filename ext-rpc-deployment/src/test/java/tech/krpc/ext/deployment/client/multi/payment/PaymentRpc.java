package tech.krpc.ext.deployment.client.multi.payment;

import tech.krpc.annotation.RpcService;
import tech.krpc.model.RpcResult;

/**
 * Multi-client fixture for EXTRPC-URL-001 — the app whose URL is overridden at runtime.
 * Its own scan package ({@code quarkus.rpc.client.paymentapp.scan}) matches only this
 * {@code @RpcService}, so {@code genRpcClientFactorys} produces exactly one
 * {@code RpcClientFactory} qualified {@code @Named("paymentapp")}.
 */
@RpcService
public interface PaymentRpc {

    RpcResult<String> charge(String orderId);
}
