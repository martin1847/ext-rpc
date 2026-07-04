package tech.krpc.ext.it;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import tech.krpc.model.RpcResult;

/**
 * Reads the INHERITED {@code tags} off the request (proving inbound deserialization of a base
 * field into a nested-DTO list), derives a summary from it SERVER-SIDE (proving the values
 * were really materialized, not passed through as opaque JSON), then echoes the tags back on
 * the reply (proving outbound serialization of an inherited field). All three legs fail
 * pre-fix, because {@link Envelope} + {@link Tag} would never be registered for native
 * reflection.
 */
@ApplicationScoped
@Startup
public class ProfileServiceImpl implements ProfileService {

    @Override
    public RpcResult<ProfileReply> describe(ProfileRequest req) {
        List<Tag> tags = req.getTags() == null ? new ArrayList<>() : req.getTags();

        // Server-side read of the inherited field: count + labels. If native reflection lost
        // the inherited tags, this is "tagCount=0; labels=" and the assertion below fails.
        String labels = tags.stream()
                .map(Tag::getLabel)
                .collect(Collectors.joining(","));

        ProfileReply reply = new ProfileReply();
        // Echo the inherited field back so the outbound JSON must serialize it.
        reply.setTags(tags);
        reply.setSummary("owner=" + req.getOwner()
                + "; tagCount=" + tags.size()
                + "; labels=" + labels);
        return RpcResult.ok(reply);
    }
}
