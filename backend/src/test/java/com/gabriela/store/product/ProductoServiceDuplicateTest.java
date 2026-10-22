package com.gabriela.store.product;

import com.gabriela.store.audit.AuditAction;
import com.gabriela.store.audit.ProductoAuditLog;
import com.gabriela.store.audit.ProductoAuditLogRepository;
import com.gabriela.store.auth.CurrentAdminService;
import com.gabriela.store.category.Categoria;
import com.gabriela.store.category.CategoriaRepository;
import com.gabriela.store.common.exception.BadRequestException;
import com.gabriela.store.common.exception.NotFoundException;
import com.gabriela.store.image.ImagenProducto;
import com.gabriela.store.image.ImagenProductoRepository;
import com.gabriela.store.product.dto.ProductDetailResponse;
import com.gabriela.store.user.UsuarioAdmin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceDuplicateTest {

    @Mock ProductoRepository productoRepository;
    @Mock CategoriaRepository categoriaRepository;
    @Mock ProductoCategoriaRepository productoCategoriaRepository;
    @Mock ProductoAuditLogRepository productoAuditLogRepository;
    @Mock CurrentAdminService currentAdminService;
    @Mock ImagenProductoRepository imagenProductoRepository;
    @Mock ProductoVarianteRepository productoVarianteRepository;
    @InjectMocks ProductoService service;

    @Test
    void duplicatesFieldsCategoriesAndActiveVariantsWithoutChangingOriginal() {
        UsuarioAdmin originalAdmin = new UsuarioAdmin("original@example.com", "Original", "unused");
        UsuarioAdmin copyingAdmin = new UsuarioAdmin("copy@example.com", "Copy", "unused");
        Producto original = new Producto(
                "Labial Rosa", new BigDecimal("25001.00"), "Descripción", "labial-rosa", "Hajuvi", originalAdmin
        );
        ReflectionTestUtils.setField(original, "idProducto", 7L);
        Categoria category = new Categoria("Labiales", "labiales");
        original.getCategorias().add(new ProductoCategoria(original, category));
        original.getImagenes().add(new ImagenProducto(original, "original-image", "https://example.com/a", 0, true, null));

        ProductoVariante active = new ProductoVariante(original, "Rosa", "#C08A7A", 3);
        ReflectionTestUtils.setField(active, "idVariante", 31L);
        ProductoVariante secondActive = new ProductoVariante(original, "Coral", "#E07060", 5);
        ReflectionTestUtils.setField(secondActive, "idVariante", 32L);
        ProductoVariante inactive = new ProductoVariante(original, "Rojo", "#FF0000", 6);
        ReflectionTestUtils.setField(inactive, "idVariante", 33L);
        inactive.desactivar();
        List<ProductoVariante> copiedVariants = new ArrayList<>();

        when(productoRepository.findByIdWithCategories(7L)).thenReturn(Optional.of(original));
        when(productoRepository.existsBySlug("labial-rosa-copia")).thenReturn(true);
        when(currentAdminService.getCurrentAdmin()).thenReturn(copyingAdmin);
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "idProducto", 99L);
            return saved;
        });
        when(productoVarianteRepository.findByProducto_IdProductoAndActivoTrueOrderByOrdenAsc(7L))
                .thenReturn(List.of(active, secondActive));
        when(productoVarianteRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ProductoVariante> saved = invocation.getArgument(0);
            long nextId = 101L;
            for (ProductoVariante variant : saved) {
                ReflectionTestUtils.setField(variant, "idVariante", nextId++);
                copiedVariants.add(variant);
            }
            return copiedVariants;
        });
        when(productoVarianteRepository.findByProducto_IdProductoAndActivoTrueOrderByOrdenAsc(99L))
                .thenAnswer(invocation -> copiedVariants);
        when(imagenProductoRepository.findByProducto_IdProductoOrderByOrdenAsc(99L)).thenReturn(List.of());

        ProductDetailResponse result = service.duplicate(7L);

        ArgumentCaptor<Producto> productCaptor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(productCaptor.capture());
        Producto copy = productCaptor.getValue();
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getIdProducto()).isEqualTo(99L);
        assertThat(copy.getNombreProducto()).isEqualTo("Labial Rosa copia");
        assertThat(copy.getPrecio()).isEqualByComparingTo(original.getPrecio());
        assertThat(copy.getDescripcion()).isEqualTo(original.getDescripcion());
        assertThat(copy.getMarca()).isEqualTo(original.getMarca());
        assertThat(copy.getSlug()).isEqualTo("labial-rosa-copia-2");
        assertThat(copy.isActivo()).isFalse();
        assertThat(copy.getCreadoPor()).isSameAs(copyingAdmin);
        assertThat(copy.getImagenes()).isEmpty();

        ArgumentCaptor<Iterable<ProductoCategoria>> categoriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(productoCategoriaRepository).saveAll(categoriesCaptor.capture());
        List<ProductoCategoria> savedCategories = new ArrayList<>();
        categoriesCaptor.getValue().forEach(savedCategories::add);
        assertThat(savedCategories).hasSize(1);
        assertThat(savedCategories.get(0).getProducto()).isSameAs(copy);
        assertThat(savedCategories.get(0).getCategoria()).isSameAs(category);
        assertThat(savedCategories.get(0)).isNotSameAs(original.getCategorias().get(0));

        assertThat(copiedVariants).hasSize(2);
        assertThat(copiedVariants.get(0).getIdVariante()).isEqualTo(101L).isNotEqualTo(active.getIdVariante());
        assertThat(copiedVariants.get(0).getProducto()).isSameAs(copy);
        assertThat(copiedVariants.get(0).getNombre()).isEqualTo(active.getNombre());
        assertThat(copiedVariants.get(0).getColorHex()).isEqualTo(active.getColorHex());
        assertThat(copiedVariants.get(0).getOrden()).isEqualTo(active.getOrden());
        assertThat(copiedVariants.get(1).getIdVariante()).isEqualTo(102L).isNotEqualTo(secondActive.getIdVariante());
        assertThat(copiedVariants.get(1).getProducto()).isSameAs(copy);
        assertThat(copiedVariants.get(1).getNombre()).isEqualTo(secondActive.getNombre());
        assertThat(copiedVariants.get(1).getColorHex()).isEqualTo(secondActive.getColorHex());
        assertThat(copiedVariants.get(1).getOrden()).isEqualTo(secondActive.getOrden());
        assertThat(result.imagenes()).isEmpty();
        assertThat(result.variantes()).hasSize(2);
        assertThat(result.categorias()).hasSize(1);

        ArgumentCaptor<ProductoAuditLog> auditCaptor = ArgumentCaptor.forClass(ProductoAuditLog.class);
        verify(productoAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getProducto()).isSameAs(copy);
        assertThat(auditCaptor.getValue().getUsuarioAdmin()).isSameAs(copyingAdmin);
        assertThat(auditCaptor.getValue().getAccion()).isEqualTo(AuditAction.CREATED);
        assertThat(auditCaptor.getValue().getDetalle()).contains("7");

        assertThat(original.getNombreProducto()).isEqualTo("Labial Rosa");
        assertThat(original.isActivo()).isTrue();
        assertThat(original.getSlug()).isEqualTo("labial-rosa");
        assertThat(original.getCreadoPor()).isSameAs(originalAdmin);
        assertThat(original.getImagenes()).hasSize(1);
        assertThat(active.getIdVariante()).isEqualTo(31L);
        assertThat(inactive.isActivo()).isFalse();
        verify(productoRepository, never()).save(original);
    }

    @Test
    void missingOriginalFailsBeforeCreatingAnything() {
        when(productoRepository.findByIdWithCategories(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicate(404L))
                .isInstanceOf(NotFoundException.class);
        verify(productoRepository, never()).save(any());
        verifyNoInteractions(productoCategoriaRepository, productoVarianteRepository, productoAuditLogRepository);
    }

    @Test
    void tooLongNameFailsBeforeCreatingAnything() {
        Producto original = new Producto("a".repeat(155), BigDecimal.ZERO, null, "a", null, null);
        when(productoRepository.findByIdWithCategories(7L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.duplicate(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("160 caracteres");
        verify(productoRepository, never()).save(any());
        verifyNoInteractions(productoCategoriaRepository, productoVarianteRepository, productoAuditLogRepository);
    }
}
