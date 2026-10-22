import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  activateAdminProduct,
  deactivateAdminProduct,
  duplicateAdminProduct,
  getAdminProducts,
  type AdminProduct,
} from "../../api/adminProductsApi";

const ITEMS_PER_BATCH = 10;

const currencyFormatter = new Intl.NumberFormat("es-CO", {
  style: "currency",
  currency: "COP",
  maximumFractionDigits: 0,
});

export function AdminProductsPage() {
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [actionProductId, setActionProductId] = useState<number | null>(null);
  const [duplicatingProductId, setDuplicatingProductId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [createdCopy, setCreatedCopy] = useState<{ id: number; nombre: string } | null>(null);
  const [visibleCount, setVisibleCount] = useState(ITEMS_PER_BATCH);
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  const duplicatingProductIdRef = useRef<number | null>(null);

  const filteredProducts = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase();

    if (!normalizedSearch) {
      return products;
    }

    return products.filter((product) => {
      return (
        product.nombre.toLowerCase().includes(normalizedSearch) ||
        product.slug.toLowerCase().includes(normalizedSearch) ||
        product.marca?.toLowerCase().includes(normalizedSearch)
      );
    });
  }, [products, searchTerm]);

  const visibleProducts = filteredProducts.slice(0, visibleCount);
  const hasMoreProducts = visibleCount < filteredProducts.length;

  useEffect(() => {
    const element = loadMoreRef.current;

    if (!element || !hasMoreProducts) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setVisibleCount((current) =>
            Math.min(current + ITEMS_PER_BATCH, filteredProducts.length),
          );
        }
      },
      {
        root: null,
        rootMargin: "300px",
        threshold: 0,
      },
    );

    observer.observe(element);

    return () => {
      observer.disconnect();
    };
  }, [hasMoreProducts, filteredProducts.length]);

  async function loadProducts() {
    try {
      setIsLoading(true);
      setErrorMessage(null);

      const response = await getAdminProducts();
      setProducts(response);
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "No fue posible cargar los productos.";

      setErrorMessage(message);
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadProducts();
  }, []);

  async function handleToggleProduct(product: AdminProduct) {
    if (actionProductId !== null || duplicatingProductIdRef.current !== null) {
      return;
    }

    const actionLabel = product.activo ? "desactivar" : "activar";

    const confirmed = window.confirm(
      `¿Seguro que quieres ${actionLabel} el producto "${product.nombre}"?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      setActionProductId(product.id);
      setErrorMessage(null);

      if (product.activo) {
        await deactivateAdminProduct(product.id);
      } else {
        await activateAdminProduct(product.id);
      }

      await loadProducts();
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : `No fue posible ${actionLabel} el producto.`;

      setErrorMessage(message);
    } finally {
      setActionProductId(null);
    }
  }

  async function handleDuplicateProduct(product: AdminProduct) {
    if (duplicatingProductIdRef.current !== null || actionProductId !== null) {
      return;
    }

    const confirmed = window.confirm(
      `¿Crear una copia inactiva de "${product.nombre}"? La copia no incluirá imágenes.`,
    );
    if (!confirmed) {
      return;
    }

    duplicatingProductIdRef.current = product.id;
    setActionProductId(product.id);
    setDuplicatingProductId(product.id);
    setErrorMessage(null);
    setCreatedCopy(null);

    try {
      const copy = await duplicateAdminProduct(product.id);
      setSearchTerm("");
      setVisibleCount(ITEMS_PER_BATCH);
      setCreatedCopy({ id: copy.id, nombre: copy.nombre });
      setProducts((current) => [
        { ...copy, imagenPrincipalUrl: null },
        ...current.filter((item) => item.id !== copy.id),
      ]);

      try {
        const refreshed = await getAdminProducts();
        const refreshedCopy = refreshed.find((item) => item.id === copy.id);
        if (refreshedCopy) {
          setProducts([
            refreshedCopy,
            ...refreshed.filter((item) => item.id !== copy.id),
          ]);
        }
      } catch {
        // La copia ya figura en el listado con los datos devueltos por el endpoint.
      }
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "No fue posible crear la copia.",
      );
    } finally {
      duplicatingProductIdRef.current = null;
      setActionProductId(null);
      setDuplicatingProductId(null);
    }
  }

  const activeCount = products.filter((product) => product.activo).length;
  const inactiveCount = products.length - activeCount;

  return (
    <main className="page admin-page">
      <section className="admin-hero">
        <div>
          <p className="section-heading__eyebrow">Catálogo interno</p>
          <h1>Productos</h1>
          <p>
            Gestiona productos activos e inactivos. Esta vista usa los endpoints
            internos de administración, no el catálogo público.
          </p>
        </div>

        <div className="admin-hero__actions">
          <Link className="secondary-button" to="/admin">
            Volver al panel
          </Link>

          <Link className="primary-button" to="/admin/products/new">
            Crear producto
          </Link>
        </div>
      </section>

      <section className="admin-toolbar">
        <div className="admin-metrics">
          <span>
            Total: <strong>{products.length}</strong>
          </span>
          <span>
            Activos: <strong>{activeCount}</strong>
          </span>
          <span>
            Inactivos: <strong>{inactiveCount}</strong>
          </span>
        </div>

        <label className="admin-search">
          <span>Buscar producto</span>
          <input
            type="search"
            value={searchTerm}
            onChange={(event) => {
              setSearchTerm(event.target.value);
              setVisibleCount(ITEMS_PER_BATCH);
            }}
            placeholder="Nombre, marca o slug"
          />
        </label>
      </section>

      {createdCopy ? (
        <div className="form-message form-message--success" role="status">
          Copia "{createdCopy.nombre}" creada como inactiva.{" "}
          <Link to={`/admin/products/${createdCopy.id}/edit`}>Editar copia</Link>
        </div>
      ) : null}

      {isLoading ? (
        <div className="state-box">Cargando productos...</div>
      ) : null}

      {errorMessage ? (
        <div className="form-message form-message--error">{errorMessage}</div>
      ) : null}

      {!isLoading && !errorMessage && filteredProducts.length === 0 ? (
        <div className="state-box">
          No hay productos que coincidan con la búsqueda.
        </div>
      ) : null}

      {!isLoading && !errorMessage && filteredProducts.length > 0 ? (
        <section className="admin-table-card">
          <div className="admin-products-table">
            <div className="admin-products-table__header">
              <span>Producto</span>
              <span>Marca</span>
              <span>Precio</span>
              <span>Estado</span>
              <span>Acciones</span>
            </div>

            {visibleProducts.map((product) => (
              <article className="admin-products-table__row" key={product.id}>
                <div className="admin-product-cell">
                  <div className="admin-product-cell__image">
                    {product.imagenPrincipalUrl ? (
                      <img
                        src={product.imagenPrincipalUrl}
                        alt={product.nombre}
                        loading="lazy"
                      />
                    ) : (
                      <span>Sin imagen</span>
                    )}
                  </div>

                  <div>
                    <strong>{product.nombre}</strong>
                    <small>{product.slug}</small>
                  </div>
                </div>

                <span>{product.marca ?? "Sin marca"}</span>

                <span>{currencyFormatter.format(product.precio)}</span>

                <span
                  className={
                    product.activo
                      ? "status-pill status-pill--active"
                      : "status-pill status-pill--inactive"
                  }
                >
                  {product.activo ? "Activo" : "Inactivo"}
                </span>

                <div className="admin-row-actions">
                  <Link
                    className="secondary-button secondary-button--small"
                    to={`/admin/products/${product.id}/edit`}
                  >
                    Editar
                  </Link>
                  <Link
                    className="secondary-button secondary-button--small"
                    to={`/admin/products/${product.id}/images`}
                  >
                    Imágenes
                  </Link>

                  <button
                    className="secondary-button secondary-button--small"
                    type="button"
                    disabled={actionProductId !== null}
                    onClick={() => handleDuplicateProduct(product)}
                  >
                    {duplicatingProductId === product.id
                      ? "Creando copia..."
                      : "Crear Copia"}
                  </button>

                  <button
                    className="secondary-button secondary-button--small"
                    type="button"
                    disabled={actionProductId !== null}
                    onClick={() => handleToggleProduct(product)}
                  >
                    {actionProductId === product.id
                      ? "Procesando..."
                      : product.activo
                        ? "Desactivar"
                        : "Activar"}
                  </button>
                </div>
              </article>
            ))}
            {hasMoreProducts ? (
              <div
                ref={loadMoreRef}
                className="catalog-load-more"
                aria-live="polite"
              >
                <div className="catalog-loading-indicator">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
            ) : null}
          </div>
        </section>
      ) : null}
    </main>
  );
}
