package tech.krpc.ext.it.noclient;

import tech.krpc.annotation.Doc;
import tech.krpc.annotation.RpcService;
import tech.krpc.model.RpcResult;

/**
 * The server-side service. Gives RpcProcessor's always-on DTO/reflection BuildStep real work to do
 * in a server-only app, so the guard exercises the server half rather than an empty index.
 */
@RpcService(description = "no-client guard: server-side service")
public interface EchoService {

    @Doc("Echoes the payload back.")
    RpcResult<EchoReply> echo(EchoRequest req);
}
