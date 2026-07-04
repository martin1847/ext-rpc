package tech.krpc.ext.it;

import tech.krpc.annotation.Doc;
import tech.krpc.annotation.RpcService;
import tech.krpc.annotation.UnsafeWeb;
import tech.krpc.model.RpcResult;

/**
 * The CNFE-fix guard service. Both param and return are {@code Child extends Envelope}, so
 * the load-bearing field ({@code List<Tag> tags}) is INHERITED, never declared on the type the
 * processor scans directly. Reaching it at native runtime requires the Jandex super-class walk
 * (RpcProcessor.recursionNestDtoType) to register {@link Envelope} + {@link Tag}. Call path
 * {@code native-it/Profile/describe}.
 */
@UnsafeWeb
@RpcService(description = "ext-rpc native-IT inherited-DTO service (CNFE fix guard)")
public interface ProfileService {

    @Doc("Echoes the inherited tags and returns a server-derived summary of them.")
    RpcResult<ProfileReply> describe(ProfileRequest req);
}
