package tech.krpc.ext.it;

/**
 * Request DTO — {@code Child extends Base}. Its own field is {@code owner}; the load-bearing
 * {@code List<Tag> tags} field is INHERITED from {@link Envelope}. The inbound rpcurl JSON
 * sets {@code tags}, so a working native image must deserialize an inherited base field into
 * a nested-DTO list — the read side of the CNFE fix.
 */
public class ProfileRequest extends Envelope {

    private String owner;

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
