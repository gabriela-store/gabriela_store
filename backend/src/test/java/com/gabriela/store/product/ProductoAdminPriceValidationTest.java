package com.gabriela.store.product;

import com.gabriela.store.audit.ProductoAuditLogService;
import com.gabriela.store.config.SecurityConfig;
import com.gabriela.store.image.ImagenProductoService;
import com.gabriela.store.product.dto.ProductDetailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductoAdminController.class)
@Import(SecurityConfig.class)
class ProductoAdminPriceValidationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductoService productoService;
    @MockitoBean ImagenProductoService imagenProductoService;
    @MockitoBean ProductoAuditLogService productoAuditLogService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void creationAccepts12550() throws Exception {
        when(productoService.create(any())).thenReturn(detail(new BigDecimal("12550")));

        mockMvc.perform(post("/api/admin/products")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("12550")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precio").value(12550));

        verify(productoService).create(org.mockito.ArgumentMatchers.argThat(
                request -> request.precio().compareTo(new BigDecimal("12550")) == 0
        ));
    }

    @Test
    void updateAccepts12599() throws Exception {
        when(productoService.update(any(), any())).thenReturn(detail(new BigDecimal("12599")));

        mockMvc.perform(put("/api/admin/products/7")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("12599")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(12599));

        verify(productoService).update(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.argThat(
                        request -> request.precio().compareTo(new BigDecimal("12599")) == 0
                ));
    }

    @Test
    void zeroIsAcceptedForCreationAndUpdate() throws Exception {
        when(productoService.create(any())).thenReturn(detail(BigDecimal.ZERO));
        when(productoService.update(any(), any())).thenReturn(detail(BigDecimal.ZERO));

        mockMvc.perform(post("/api/admin/products")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("0")))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/products/7")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("0")))
                .andExpect(status().isOk());
    }

    @Test
    void maximumStoredIntegerIsAccepted() throws Exception {
        when(productoService.create(any())).thenReturn(detail(new BigDecimal("9999999999")));

        mockMvc.perform(post("/api/admin/products")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("9999999999")))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-100", "12500.05", "10000000000", "\"Infinity\"", "\"NaN\""})
    void invalidPricesAreRejectedForCreationAndUpdate(String price) throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body(price)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/products/7")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body(price)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productoService);
    }

    @Test
    void missingPriceIsRejectedForCreationAndUpdate() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "precio: El precio del producto es obligatorio."
                ));
        mockMvc.perform(put("/api/admin/products/7")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productoService);
    }

    private static String body(String price) {
        String priceField = price == null ? "" : "\"precio\":" + price + ",";
        return "{\"nombre\":\"Labial\"," + priceField
                + "\"categoriaIds\":[1],\"variantes\":[]}";
    }

    private static ProductDetailResponse detail(BigDecimal price) {
        return new ProductDetailResponse(
                7L, "Labial", price, null, "labial", true, null,
                List.of(), List.of(), List.of()
        );
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
