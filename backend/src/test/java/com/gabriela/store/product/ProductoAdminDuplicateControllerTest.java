package com.gabriela.store.product;

import com.gabriela.store.audit.ProductoAuditLogService;
import com.gabriela.store.common.exception.BadRequestException;
import com.gabriela.store.common.exception.NotFoundException;
import com.gabriela.store.config.SecurityConfig;
import com.gabriela.store.image.ImagenProductoService;
import com.gabriela.store.product.dto.ProductDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductoAdminController.class)
@Import(SecurityConfig.class)
class ProductoAdminDuplicateControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductoService productoService;
    @MockitoBean ImagenProductoService imagenProductoService;
    @MockitoBean ProductoAuditLogService productoAuditLogService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void adminGetsCreatedProductDetail() throws Exception {
        when(productoService.duplicate(7L)).thenReturn(new ProductDetailResponse(
                99L, "Labial Rosa copia", new BigDecimal("25001.00"), "Descripción",
                "labial-rosa-copia", false, "Hajuvi", List.of(), List.of(), List.of()
        ));

        mockMvc.perform(post("/api/admin/products/7/duplicate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.imagenes").isEmpty());
        verify(productoService).duplicate(7L);
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        when(productoService.duplicate(404L)).thenThrow(new NotFoundException("Producto no encontrado con id: 404"));

        mockMvc.perform(post("/api/admin/products/404/duplicate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Producto no encontrado con id: 404"));
    }

    @Test
    void tooLongNameReturnsBadRequest() throws Exception {
        when(productoService.duplicate(7L)).thenThrow(new BadRequestException(
                "No se puede crear la copia: el nombre resultante supera los 160 caracteres."
        ));

        mockMvc.perform(post("/api/admin/products/7/duplicate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "No se puede crear la copia: el nombre resultante supera los 160 caracteres."
                ));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/products/7/duplicate"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productoService);
    }

    @Test
    void customerCannotDuplicate() throws Exception {
        mockMvc.perform(post("/api/admin/products/7/duplicate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productoService);
    }
}
