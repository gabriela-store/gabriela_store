package com.gabriela.store.product;

import com.gabriela.store.audit.AuditAction;
import com.gabriela.store.audit.ProductoAuditLog;
import com.gabriela.store.audit.ProductoAuditLogRepository;
import com.gabriela.store.auth.CurrentAdminService;
import com.gabriela.store.category.Categoria;
import com.gabriela.store.category.CategoriaRepository;
import com.gabriela.store.category.dto.CategoryResponse;
import com.gabriela.store.common.exception.BadRequestException;
import com.gabriela.store.common.exception.NotFoundException;
import com.gabriela.store.common.text.SlugUtils;
import com.gabriela.store.image.ImagenProducto;
import com.gabriela.store.image.ImagenProductoRepository;
import com.gabriela.store.image.dto.ImageResponse;
import com.gabriela.store.product.dto.ProductCreateRequest;
import com.gabriela.store.product.dto.ProductDetailResponse;
import com.gabriela.store.product.dto.ProductResponse;
import com.gabriela.store.product.dto.ProductUpdateRequest;
import com.gabriela.store.user.UsuarioAdmin;
import com.gabriela.store.user.UsuarioAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gabriela.store.product.dto.ProductVariantRequest;
import com.gabriela.store.product.dto.ProductVariantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import java.util.HashSet;
import java.util.List;

@Service
public class ProductoService {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoCategoriaRepository productoCategoriaRepository;
    private final ProductoAuditLogRepository productoAuditLogRepository;
    private final CurrentAdminService currentAdminService;
    private final ImagenProductoRepository imagenProductoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;

    public ProductoService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            ProductoCategoriaRepository productoCategoriaRepository,
            ProductoAuditLogRepository productoAuditLogRepository,
            CurrentAdminService currentAdminService,
            ImagenProductoRepository imagenProductoRepository,
            ProductoVarianteRepository productoVarianteRepository
    ) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.productoCategoriaRepository = productoCategoriaRepository;
        this.productoAuditLogRepository = productoAuditLogRepository;
        this.currentAdminService = currentAdminService;
        this.imagenProductoRepository = imagenProductoRepository;
        this.productoVarianteRepository = productoVarianteRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findActiveProducts(
            String categorySlug,
            String search,
            Pageable pageable
    ) {
        String normalizedCategory = normalizeFilter(categorySlug);
        String searchPattern = buildSearchPattern(search);

        return productoRepository
                .findActiveProducts(
                        normalizedCategory,
                        searchPattern,
                        pageable
                )
                .map(this::toSummaryResponse);
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.trim();
    }

    private String buildSearchPattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }



    // este metodo devuelve un producto por su slug, si el producto no existe o no esta activo, lanza una excepcion
    @Transactional(readOnly = true)
    public ProductDetailResponse findBySlug(String slug) {
        Producto producto = productoRepository.findBySlugWithCategories(slug)
                .filter(Producto::isActivo)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + slug));

        return toDetailResponse(producto);
    }


    // el siguiente metodo crea un nuevo producto, recibe un objeto ProductCreateRequest
    // con los datos del producto a crear, valida que las categorias existan y que no haya
    // categorias duplicadas, genera un slug unico, guarda el producto y las relaciones
    // con las categorias, y finalmente devuelve el producto creado en formato de respuesta detallada
    @Transactional
    public ProductDetailResponse create(ProductCreateRequest request) {
        validateUniqueCategoryIds(request.categoriaIds());

        List<Categoria> categorias = categoriaRepository.findAllById(request.categoriaIds());

        if (categorias.size() != request.categoriaIds().size()) {
            throw new BadRequestException("Una o más categorías no existen.");
        }

        String slug = generateUniqueSlug(request.nombre());

        UsuarioAdmin admin = currentAdminService.getCurrentAdmin();

        Producto producto = new Producto(
                request.nombre().trim(),
                request.precio(),
                normalizeNullableText(request.descripcion()),
                slug,
                normalizeNullableText(request.marca()),
                admin
        );

        Producto savedProduct = productoRepository.save(producto);

        List<ProductoCategoria> relaciones = categorias.stream()
                .map(categoria -> new ProductoCategoria(savedProduct, categoria))
                .toList();

        productoCategoriaRepository.saveAll(relaciones);
        syncVariants(savedProduct, request.variantes() == null ? List.of() : request.variantes());

        productoAuditLogRepository.save(new ProductoAuditLog(
                savedProduct,
                admin,
                AuditAction.CREATED,
                "Producto creado desde API admin."
        ));

        List<ProductoCategoria> savedRelations =
                productoCategoriaRepository.findByProducto_IdProducto(savedProduct.getIdProducto());

        return toDetailResponse(savedProduct, savedRelations);
    }

    @Transactional
    public ProductDetailResponse duplicate(Long idProducto) {
        Producto original = productoRepository.findByIdWithCategories(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con id: " + idProducto));

        String copyName = original.getNombreProducto() + " copia";
        if (copyName.length() > 160) {
            throw new BadRequestException(
                    "No se puede crear la copia: el nombre resultante supera los 160 caracteres."
            );
        }

        UsuarioAdmin admin = currentAdminService.getCurrentAdmin();
        Producto copy = new Producto(
                copyName,
                original.getPrecio(),
                original.getDescripcion(),
                generateUniqueSlug(copyName),
                original.getMarca(),
                admin
        );
        copy.desactivar(admin);
        Producto savedCopy = productoRepository.save(copy);

        List<ProductoCategoria> relations = original.getCategorias().stream()
                .map(relation -> new ProductoCategoria(savedCopy, relation.getCategoria()))
                .toList();
        productoCategoriaRepository.saveAll(relations);

        List<ProductoVariante> variants = productoVarianteRepository
                .findByProducto_IdProductoAndActivoTrueOrderByOrdenAsc(idProducto)
                .stream()
                .map(variant -> new ProductoVariante(
                        savedCopy,
                        variant.getNombre(),
                        variant.getColorHex(),
                        variant.getOrden()
                ))
                .toList();
        productoVarianteRepository.saveAll(variants);

        productoAuditLogRepository.save(new ProductoAuditLog(
                savedCopy,
                admin,
                AuditAction.CREATED,
                "Copia creada desde el producto de origen con id " + idProducto + "."
        ));

        return toDetailResponse(savedCopy, relations);
    }


    private ProductDetailResponse toDetailResponse(Producto producto) {
        return toDetailResponse(producto, producto.getCategorias());
    }



    // el siguiente metodo valida que la lista de categorias no sea nula ni vacia, y que no
    // contenga ids duplicados, si alguna de estas condiciones no se cumple, lanza una excepcion
    private void validateUniqueCategoryIds(List<Long> categoriaIds) {
        if (categoriaIds == null || categoriaIds.isEmpty()) {
            throw new BadRequestException("El producto debe tener al menos una categoría.");
        }

        if (new HashSet<>(categoriaIds).size() != categoriaIds.size()) {
            throw new BadRequestException("La lista de categorías contiene IDs duplicados.");
        }
    }


    // aqui se genera un slug unico a partir del nombre del producto, si el slug generado
    // ya existe en la base de datos, se le agrega un sufijo numerico hasta encontrar
    // uno que no exista, y se devuelve el slug unico generado, esto garantiza que no haya
    // conflictos de slugs entre productos, incluso si tienen nombres similares o iguales
    private String generateUniqueSlug(String nombre) {
        String baseSlug = SlugUtils.toSlug(nombre);

        String candidate = baseSlug;
        int suffix = 2;

        while (productoRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }

        return candidate;
    }

    //el siguiente metodo normaliza un texto que puede ser nulo, si el valor es nulo o
    // esta en blanco, devuelve null, de lo contrario devuelve el texto recortado, esto
    // se utiliza para campos opcionales como descripcion o marca, para evitar almacenar
    // cadenas vacias o con espacios en blanco, y en su lugar almacenar null en la base
    // de datos, lo que facilita las consultas y el manejo de datos nulos

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }






    // aqui se convierten los productos a un formato de respuesta mas simple, sin las categorias asociadas, para ser utilizado en la lista de productos
    private ProductResponse toSummaryResponse(Producto producto) {
        String imagenPrincipalUrl = imagenProductoRepository
                .findByProducto_IdProductoAndPrincipalTrue(producto.getIdProducto())
                .map(ImagenProducto::getUrl)
                .orElse(null);

        return new ProductResponse(
                producto.getIdProducto(),
                producto.getNombreProducto(),
                producto.getPrecio(),
                producto.getDescripcion(),
                producto.getSlug(),
                producto.isActivo(),
                producto.getMarca(),
                imagenPrincipalUrl,
                productoVarianteRepository.existsByProducto_IdProductoAndActivoTrue(producto.getIdProducto())
        );
    }


    // este metodo convierte un producto a un formato de respuesta mas detallado, incluyendo las categorias asociadas, para ser utilizado en la vista de detalle del producto
    private ProductDetailResponse toDetailResponse(Producto producto, List<ProductoCategoria> relaciones) {
        List<CategoryResponse> categorias = relaciones
                .stream()
                .map(ProductoCategoria::getCategoria)
                .map(categoria -> new CategoryResponse(
                        categoria.getIdCategoria(),
                        categoria.getNombre(),
                        categoria.getSlug()
                ))
                .toList();

        List<ImageResponse> imagenes = imagenProductoRepository
                .findByProducto_IdProductoOrderByOrdenAsc(producto.getIdProducto())
                .stream()
                .map(this::toImageResponse)
                .toList();

        List<ProductVariantResponse> variantes = productoVarianteRepository
                .findByProducto_IdProductoAndActivoTrueOrderByOrdenAsc(producto.getIdProducto())
                .stream()
                .map(this::toVariantResponse)
                .toList();

        return new ProductDetailResponse(
                producto.getIdProducto(),
                producto.getNombreProducto(),
                producto.getPrecio(),
                producto.getDescripcion(),
                producto.getSlug(),
                producto.isActivo(),
                producto.getMarca(),
                categorias,
                imagenes,
                variantes
        );
    }


    // este metodo convierte una entidad de imagen de producto a un formato de respuesta,
    // para ser utilizado en la vista de detalle del producto, incluye el id de la imagen,
    // la url, el orden, si es principal o no, y el texto alternativo
    private ImageResponse toImageResponse(ImagenProducto imagen) {
        return new ImageResponse(
                imagen.getIdImagen(),
                imagen.getUrl(),
                imagen.getOrden(),
                imagen.isPrincipal(),
                imagen.getAltText()
        );
    }

    // este metodo actualiza un producto existente, recibe el id del producto a actualizar
    // y un objeto ProductUpdateRequest con los nuevos datos del producto, valida que el
    // producto exista, que las categorias existan y no haya categorias duplicadas, genera
    // un slug unico para la actualizacion, actualiza los datos del producto, guarda el producto
    // y las nuevas relaciones con las categorias, y finalmente devuelve el producto actualizado
    // en formato de respuesta detallada, ademas registra la actualizacion en el log de
    // auditoria con el usuario admin que realizo la actualizacion, y una descripcion
    // de la accion realizada
    @Transactional
    public ProductDetailResponse update(Long idProducto, ProductUpdateRequest request) {
        validateUniqueCategoryIds(request.categoriaIds());

        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con id: " + idProducto));

        List<Categoria> categorias = categoriaRepository.findAllById(request.categoriaIds());

        if (categorias.size() != request.categoriaIds().size()) {
            throw new BadRequestException("Una o más categorías no existen.");
        }

        UsuarioAdmin admin = currentAdminService.getCurrentAdmin();

        String slug = generateUniqueSlugForUpdate(request.nombre(), producto.getIdProducto());

        producto.actualizarDatos(
                request.nombre().trim(),
                request.precio(),
                normalizeNullableText(request.descripcion()),
                normalizeNullableText(request.marca()),
                slug,
                admin
        );


        Producto savedProduct = productoRepository.save(producto);

        productoCategoriaRepository.deleteAllByProductoId(savedProduct.getIdProducto());
        productoCategoriaRepository.flush();

        List<ProductoCategoria> nuevasRelaciones = categorias.stream()
                .map(categoria -> new ProductoCategoria(savedProduct, categoria))
                .toList();

        productoCategoriaRepository.saveAll(nuevasRelaciones);

        if (request.variantes() != null) {
            syncVariants(savedProduct, request.variantes());
        }

        productoAuditLogRepository.save(new ProductoAuditLog(
                savedProduct,
                admin,
                AuditAction.UPDATED,
                "Producto actualizado desde API admin."
        ));

        List<ProductoCategoria> savedRelations =
                productoCategoriaRepository.findByProducto_IdProducto(savedProduct.getIdProducto());

        return toDetailResponse(savedProduct, savedRelations);
    }


    // este metodo genera un slug unico para la actualizacion de un producto,
    // recibe el nuevo nombre del producto y el id del producto que se esta actualizando,
    // genera un slug base a partir del nuevo nombre, y luego verifica si ese slug ya existe
    // en la base de datos para otro producto diferente al que se esta actualizando,
    // si existe un conflicto de slug, agrega un sufijo numerico al slug base hasta
    // encontrar un slug unico, y devuelve el slug unico generado, esto garantiza que al
    // actualizar el nombre de un producto, el slug se mantenga unico y no haya conflictos
    // con otros productos, incluso si el nuevo nombre es similar o igual al de otro producto,
    // el slug se ajustara automaticamente para evitar conflictos y mantener la
    // integridad de los slugs en la base de datos.
    private String generateUniqueSlugForUpdate(String nombre, Long currentProductId) {
        String baseSlug = SlugUtils.toSlug(nombre);

        String candidate = baseSlug;
        int suffix = 2;

        while (productoRepository.existsBySlugAndIdProductoNot(candidate, currentProductId)) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }

        return candidate;
    }



    // este metodo desactiva un producto existente, recibe el id del producto a desactivar,
    // valida que el producto exista, cambia su estado a inactivo, guarda el producto,
    // y finalmente devuelve el producto desactivado en formato de respuesta detallada,
    // ademas registra la desactivacion en el log de auditoria con el usuario admin que
    // realizo la desactivacion, y una descripcion de la accion realizada
    @Transactional
    public ProductDetailResponse deactivate(Long idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con id: " + idProducto));

        UsuarioAdmin admin = currentAdminService.getCurrentAdmin();

        boolean changed = producto.desactivar(admin);

        Producto savedProduct = productoRepository.save(producto);

        if (changed) {
            productoAuditLogRepository.save(new ProductoAuditLog(
                    savedProduct,
                    admin,
                    AuditAction.DEACTIVATED,
                    "Producto desactivado desde API admin."
            ));
        }

        List<ProductoCategoria> relaciones =
                productoCategoriaRepository.findByProducto_IdProducto(savedProduct.getIdProducto());

        return toDetailResponse(savedProduct, relaciones);
    }

    // este metodo activa un producto existente, recibe el id del producto a activar,
    // valida que el producto exista, cambia su estado a activo, guarda el producto,
    // y finalmente devuelve el producto activado en formato de respuesta detallada,
    // ademas registra la activacion en el log de auditoria con el usuario admin que
    // realizo la activacion, y una descripcion de la accion realizada

    @Transactional
    public ProductDetailResponse activate(Long idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con id: " + idProducto));

        UsuarioAdmin admin = currentAdminService.getCurrentAdmin();

        boolean changed = producto.activar(admin);

        Producto savedProduct = productoRepository.save(producto);

        if (changed) {
            productoAuditLogRepository.save(new ProductoAuditLog(
                    savedProduct,
                    admin,
                    AuditAction.REACTIVATED,
                    "Producto reactivado desde API admin."
            ));
        }

        List<ProductoCategoria> relaciones =
                productoCategoriaRepository.findByProducto_IdProducto(savedProduct.getIdProducto());

        return toDetailResponse(savedProduct, relaciones);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAllForAdmin() {
        return productoRepository.findAllForAdmin()
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse findByIdForAdmin(Long idProducto) {
        Producto producto = productoRepository.findByIdWithCategories(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con id: " + idProducto));

        return toDetailResponse(producto);
    }

    private void syncVariants(Producto producto, List<ProductVariantRequest> requestedVariants) {
        List<ProductVariantRequest> normalizedRequests = normalizeVariantRequests(requestedVariants);

        List<ProductoVariante> existingVariants =
                productoVarianteRepository.findByProducto_IdProductoOrderByOrdenAsc(producto.getIdProducto());

        Map<Long, ProductoVariante> existingById = new HashMap<>();

        for (ProductoVariante variant : existingVariants) {
            existingById.put(variant.getIdVariante(), variant);
        }

        Set<Long> requestedExistingIds = normalizedRequests
                .stream()
                .map(ProductVariantRequest::id)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        List<ProductoVariante> variantsToSave = new ArrayList<>();

        for (int index = 0; index < normalizedRequests.size(); index++) {
            ProductVariantRequest request = normalizedRequests.get(index);

            String name = normalizeVariantName(request.nombre());
            String colorHex = normalizeColorHex(request.colorHex());

            if (request.id() != null) {
                ProductoVariante existing = existingById.get(request.id());

                if (existing == null) {
                    throw new BadRequestException(
                            "La variante " + request.id() + " no pertenece al producto " + producto.getIdProducto() + "."
                    );
                }

                existing.actualizarDatos(name, colorHex, index);
                variantsToSave.add(existing);
            } else {
                variantsToSave.add(new ProductoVariante(
                        producto,
                        name,
                        colorHex,
                        index
                ));
            }
        }

        for (ProductoVariante existing : existingVariants) {
            if (!requestedExistingIds.contains(existing.getIdVariante())) {
                existing.desactivar();
                variantsToSave.add(existing);
            }
        }

        productoVarianteRepository.saveAll(variantsToSave);
    }

    private List<ProductVariantRequest> normalizeVariantRequests(
            List<ProductVariantRequest> requestedVariants
    ) {
        if (requestedVariants == null || requestedVariants.isEmpty()) {
            return List.of();
        }

        List<ProductVariantRequest> normalized = new ArrayList<>();
        Set<String> seenNames = new java.util.HashSet<>();

        for (ProductVariantRequest request : requestedVariants) {
            if (request == null) {
                throw new BadRequestException("La variante no puede ser nula.");
            }

            String name = normalizeVariantName(request.nombre());
            String colorHex = normalizeColorHex(request.colorHex());

            String nameKey = name.toLowerCase(Locale.ROOT);

            if (!seenNames.add(nameKey)) {
                throw new BadRequestException("No puede haber tonos duplicados en el mismo producto: " + name + ".");
            }

            normalized.add(new ProductVariantRequest(
                    request.id(),
                    name,
                    colorHex
            ));
        }

        return normalized;
    }

    private String normalizeVariantName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("El nombre del tono es obligatorio.");
        }

        String trimmed = name.trim();

        if (trimmed.length() > 80) {
            throw new BadRequestException("El nombre del tono no puede superar 80 caracteres.");
        }

        return trimmed;
    }

    private String normalizeColorHex(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            throw new BadRequestException("El color del tono es obligatorio.");
        }

        String normalized = colorHex.trim();

        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("El color debe tener formato hexadecimal, por ejemplo #C08A7A.");
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    private ProductVariantResponse toVariantResponse(ProductoVariante variante) {
        return new ProductVariantResponse(
                variante.getIdVariante(),
                variante.getNombre(),
                variante.getColorHex(),
                variante.getOrden(),
                variante.isActivo()
        );
    }
}
