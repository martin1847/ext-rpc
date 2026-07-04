package tech.krpc.ext.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

/**
 * Pins the Jandex super-class-chain walk in
 * {@link RpcProcessor#recursionNestDtoType(org.jboss.jandex.IndexView, Set)}.
 *
 * <p>These assertions are mutation-kill tests: they go red if the super-class walk is
 * deleted (inherited field types would be lost) or if the impl reverts to a raw
 * {@code Class.forName}/{@code getSuperclass()} walk (the generic super-type arg would be
 * erased and lost).
 */
class RpcProcessorNestDtoTest {

    // ---- Fixtures: static nested classes so no synthetic this$0 field leaks the outer type.

    static class InnerDto {
        String x;
    }

    static class ItemDto {
        int n;
    }

    static class PayloadDto {
        String p;
    }

    static class Base {
        InnerDto inner;
        List<ItemDto> items;
        String name;
    }

    static class Child extends Base {
        long id;
    }

    static class GenericBase<T> {
        T payload;
        String label;
    }

    static class GenericChild extends GenericBase<PayloadDto> {
    }

    /**
     * Simulates an external library base that is NOT in the Jandex index. Its own declared field
     * ({@link #secret}) can never be reached, since the walk cannot resolve a non-indexed class.
     */
    static class ExternalBase {
        InnerDto secret;
    }

    static class ChildOfExternal extends ExternalBase {
        long id;
    }

    private static Index buildIndex() throws IOException {
        return Index.of(
                InnerDto.class,
                ItemDto.class,
                PayloadDto.class,
                Base.class,
                Child.class,
                GenericBase.class,
                GenericChild.class);
    }

    @Test
    void inheritedFieldTypesAreCollectedViaSuperClassWalk() throws IOException {
        Index index = buildIndex();

        Set<String> result = RpcProcessor.recursionNestDtoType(index, Set.of(Child.class.getName()));

        // Field types declared on the super-class Base must be collected — this is only
        // possible if the impl walks the super-class chain (Jandex fields() is declared-only).
        assertTrue(result.contains(Base.class.getName()),
                "expected super-class Base to be collected, got: " + result);
        assertTrue(result.contains(InnerDto.class.getName()),
                "expected inherited field type InnerDto to be collected, got: " + result);
        assertTrue(result.contains(ItemDto.class.getName()),
                "expected inherited List<ItemDto> arg ItemDto to be collected, got: " + result);

        // The DTO itself is an input, never re-registered as a child.
        assertFalse(result.contains(Child.class.getName()),
                "input Child must not appear in the child set, got: " + result);

        // No java.* type (String, Object, ...) ever leaks in.
        assertFalse(result.stream().anyMatch(n -> n.startsWith("java.")),
                "no java.* type should be collected, got: " + result);
    }

    @Test
    void genericSuperTypeArgumentIsCollected() throws IOException {
        Index index = buildIndex();

        Set<String> result = RpcProcessor.recursionNestDtoType(index, Set.of(GenericChild.class.getName()));

        // GenericChild extends GenericBase<PayloadDto>. Only the Jandex superClassType()
        // (a ParameterizedType) preserves the actual type argument; a raw getSuperclass()
        // walk erases it and would miss PayloadDto.
        assertTrue(result.contains(PayloadDto.class.getName()),
                "expected generic super-type arg PayloadDto to be collected, got: " + result);
    }

    @Test
    void flatDtoWithOnlyJavaFieldsCollectsNothing() throws IOException {
        Index index = buildIndex();

        Set<String> result = RpcProcessor.recursionNestDtoType(index, Set.of(InnerDto.class.getName()));

        // InnerDto has only a String field -> nothing extra to register.
        assertTrue(result.isEmpty(),
                "flat DTO should register nothing extra, got: " + result);
    }

    @Test
    void nonIndexedBaseStopsWalkWithoutCollectingItsFields() throws IOException {
        // Index the child but deliberately NOT its super-class ExternalBase, mimicking an
        // external library base absent from the CombinedIndex. This drives the walk's
        // "superName present but getClassByName == null" branch, which logs a WARN and stops.
        Index index = Index.of(ChildOfExternal.class, InnerDto.class);

        Set<String> result =
                RpcProcessor.recursionNestDtoType(index, Set.of(ChildOfExternal.class.getName()));

        // The walk must terminate gracefully (no crash on the null super-class).
        // ExternalBase itself is a non-indexed CLASS and is collected as a type to register...
        assertTrue(result.contains(ExternalBase.class.getName()),
                "expected the non-indexed base ExternalBase to still be registered, got: " + result);
        // ...but its own declared field type (InnerDto) is unreachable because the walk cannot
        // descend into a class the index does not have.
        assertFalse(result.contains(InnerDto.class.getName()),
                "InnerDto is declared on the non-indexed base and must be unreachable, got: " + result);
    }
}
