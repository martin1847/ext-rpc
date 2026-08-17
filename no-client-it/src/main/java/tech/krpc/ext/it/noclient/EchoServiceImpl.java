package tech.krpc.ext.it.noclient;

import jakarta.enterprise.context.ApplicationScoped;
import tech.krpc.model.RpcResult;

@ApplicationScoped
public class EchoServiceImpl implements EchoService {

    @Override
    public RpcResult<EchoReply> echo(EchoRequest req) {
        return RpcResult.ok(new EchoReply(req.getPayload()));
    }
}
