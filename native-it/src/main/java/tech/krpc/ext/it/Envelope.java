package tech.krpc.ext.it;

import java.util.List;

/**
 * Base DTO carrying the non-trivial INHERITED field {@code List<Tag> tags}. Both the request
 * and reply DTOs extend this, so the field is never declared on the concrete type the
 * processor scans directly — it is only reachable by walking the super-class chain.
 *
 * <p>This is exactly the shape the CNFE fix targets: the Jandex super-class walk in
 * {@code RpcProcessor.recursionNestDtoType} must (a) register this base class so its inherited
 * getter/field is reflectively visible, and (b) descend into {@code List<Tag>} to register the
 * nested {@link Tag}. Pre-fix, the {@code Class.forName}-based walk threw
 * {@link ClassNotFoundException} in the native augmentation classloader and silently skipped
 * both — the child registered, but its inherited {@code tags} vanished at native runtime.
 */
public class Envelope {

    private List<Tag> tags;

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }
}
