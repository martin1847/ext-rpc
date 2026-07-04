package tech.krpc.ext.it;

import jakarta.validation.constraints.NotBlank;

/**
 * Flat request DTO (no inheritance) — the baseline path. jakarta.validation (SPEC §7):
 * a blank name is rejected with INVALID_ARGUMENT before the method runs.
 */
public class HelloRequest {

    @NotBlank
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
