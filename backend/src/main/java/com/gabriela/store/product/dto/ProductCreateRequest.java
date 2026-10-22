package com.gabriela.store.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record ProductCreateRequest(
        @NotBlank(message = "El nombre del producto es obligatorio.")
        @Size(max = 160, message = "El nombre no puede superar los 160 caracteres.")
        String nombre,

        @NotNull(message = "El precio del producto es obligatorio.")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo.")
        @DecimalMax(value = "9999999999", message = "El precio no puede superar 9.999.999.999 COP.")
        @Digits(integer = 10, fraction = 0, message = "El precio debe ser un entero de hasta 9.999.999.999 COP.")
        BigDecimal precio,

        @Size(max = 3000, message = "La descripción no puede superar los 3000 caracteres.")
        String descripcion,

        @Size(max=100, message = "La marca no puede superar los 100 caracteres.")
        String marca,

        @NotEmpty(message = "El producto debe tener al menos una categoría.")
        List<Long> categoriaIds,

        @Valid
        List<ProductVariantRequest> variantes
) {
}
