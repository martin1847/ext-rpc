package tech.krpc.ext.it.client;

import tech.krpc.annotation.RpcService;
import tech.krpc.model.RpcResult;

/**
 * EXTRPC-URL-001 native env-override guard — the CLIENT side.
 *
 * <p>Isolated in its own package ({@code tech.krpc.ext.it.client}) and matched only by
 * {@code quarkus.rpc.client.payment-server.scan=tech.krpc.ext.it.client}, so the client factory
 * binds to THIS interface and never to the server-exposed Hello/Profile services (which live in
 * {@code tech.krpc.ext.it} and must stay server-registered — RpcProcessor removes any client-matched
 * @RpcService from the server reflective set).
 *
 * <p>No server impl exists for it here: native-it only needs the client factory to be built at
 * runtime so its dial target (env-overridden) shows up in the boot log. Flat String I/O keeps DTO
 * reflection trivial.
 */
@RpcService(description = "ext-rpc native-IT client-url guard (no server impl)")
public interface PaymentRpc {

    RpcResult<String> prepay(String orderId);
}
