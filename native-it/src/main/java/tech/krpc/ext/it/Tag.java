package tech.krpc.ext.it;

/**
 * Nested custom DTO reachable ONLY through the inherited {@link Envelope#getTags()} field.
 * The CNFE fix must walk {@code ProfileRequest} → {@code Envelope} (super-class), find
 * {@code List<Tag>}, and register {@code Tag} for native reflection. Pre-fix, the
 * augmentation-phase {@code Class.forName} walk threw CNFE and skipped this entirely, so at
 * native runtime {@code Tag} could not deserialize (its {@code label} field would be lost).
 */
public class Tag {

    private String label;
    private Integer weight;

    public Tag() {
    }

    public Tag(String label, Integer weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }
}
