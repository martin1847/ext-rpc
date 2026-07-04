package tech.krpc.ext.it;

/**
 * Flat response DTO. Boxed scalar (SPEC §4): JSON serialization is NON_NULL, so a boxed
 * type preserves the "absent vs zero" distinction a primitive would destroy.
 */
public class HelloReply {

    private String message;
    private Long timestamp;

    public HelloReply() {
    }

    public HelloReply(String message, Long timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
