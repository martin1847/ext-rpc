package tech.krpc.ext.it.noclient;

public class EchoReply {

    private String payload;

    public EchoReply() {
    }

    public EchoReply(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
