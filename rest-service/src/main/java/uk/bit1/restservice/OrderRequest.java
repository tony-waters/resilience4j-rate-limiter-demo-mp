package uk.bit1.restservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderRequest(
        @NotBlank String product,
        @Positive int quantity,
        @NotBlank String customerEmail
) {
}
