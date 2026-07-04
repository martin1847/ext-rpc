package tech.krpc.ext.it;

/**
 * Reply DTO — {@code Child extends Base}. Its own field is {@code summary}; the load-bearing
 * {@code List<Tag> tags} field is INHERITED from {@link Envelope} and echoed back. The
 * outbound rpcurl JSON must therefore contain the inherited {@code tags} with the nested
 * {@link Tag} values — the write side of the CNFE fix.
 */
public class ProfileReply extends Envelope {

    private String summary;

    public ProfileReply() {
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
