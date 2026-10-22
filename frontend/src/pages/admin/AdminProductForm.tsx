import { useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import type { AdminCategory } from "../../api/adminCategoriesApi";
import type { AdminProductUpsertRequest } from "../../api/adminProductsApi";

export type AdminProductVariantFormValue = {
  id?: number;
  nombre: string;
  colorHex: string;
};

export type AdminProductFormValues = {
  nombre: string;
  precio: string;
  descripcion: string;
  marca: string;
  categoriaIds: number[];
  variantes: AdminProductVariantFormValue[];
};

type AdminProductFormProps = {
  eyebrow: string;
  title: string;
  description: string;
  submitLabel: string;
  categories: AdminCategory[];
  initialValues: AdminProductFormValues;
  isSubmitting: boolean;
  errorMessage: string | null;
  successMessage?: ReactNode;
  afterContent?: ReactNode;
  onCancel: () => void;
  onSubmit: (request: AdminProductUpsertRequest) => Promise<void>;
};

const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;
const MAX_PRICE_COP = 9_999_999_999;

function validatePrice(value: string): string | null {
  const raw = value.trim();
  if (!raw) {
    return "El precio del producto es obligatorio.";
  }

  if (!/^\d+$/.test(raw)) {
    if (/^-\d+(?:[.,]\d+)?$/.test(raw)) {
      return "El precio no puede ser negativo.";
    }
    if (/^\d+[.,]\d+$/.test(raw)) {
      return "El precio debe ser un entero de 0 hasta 9.999.999.999 COP.";
    }
    return "Ingresa un precio válido en pesos enteros.";
  }

  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed > MAX_PRICE_COP) {
    return "El precio no puede superar 9.999.999.999 COP.";
  }

  return null;
}

export function AdminProductForm({
  eyebrow,
  title,
  description,
  submitLabel,
  categories,
  initialValues,
  isSubmitting,
  errorMessage,
  successMessage,
  afterContent,
  onCancel,
  onSubmit,
}: AdminProductFormProps) {
  const [nombre, setNombre] = useState(initialValues.nombre);
  const [precio, setPrecio] = useState(initialValues.precio);
  const [descripcion, setDescripcion] = useState(initialValues.descripcion);
  const [marca, setMarca] = useState(initialValues.marca);
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<number[]>(
    initialValues.categoriaIds,
  );
  const [variantes, setVariantes] = useState<AdminProductVariantFormValue[]>(
    initialValues.variantes ?? [],
  );
  const [validationMessage, setValidationMessage] = useState<string | null>(
    null,
  );

  useEffect(() => {
    setNombre(initialValues.nombre);
    setPrecio(initialValues.precio);
    setDescripcion(initialValues.descripcion);
    setMarca(initialValues.marca);
    setSelectedCategoryIds(initialValues.categoriaIds);
    setVariantes(initialValues.variantes ?? []);
    setValidationMessage(null);
  }, [initialValues]);

  function handleCategoryChange(categoryId: number, checked: boolean) {
    setSelectedCategoryIds((current) => {
      if (checked) {
        return current.includes(categoryId) ? current : [...current, categoryId];
      }

      return current.filter((id) => id !== categoryId);
    });
  }

  function handleAddVariant() {
    setVariantes((current) => [
      ...current,
      {
        nombre: "",
        colorHex: "#C08A7A",
      },
    ]);
  }

  function handleRemoveVariant(indexToRemove: number) {
    setVariantes((current) =>
      current.filter((_, index) => index !== indexToRemove),
    );
  }

  function handleVariantNameChange(indexToUpdate: number, value: string) {
    setVariantes((current) =>
      current.map((variant, index) =>
        index === indexToUpdate ? { ...variant, nombre: value } : variant,
      ),
    );
  }

  function handleVariantColorChange(indexToUpdate: number, value: string) {
    setVariantes((current) =>
      current.map((variant, index) =>
        index === indexToUpdate ? { ...variant, colorHex: value } : variant,
      ),
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const normalizedName = nombre.trim();
    const normalizedDescription = descripcion.trim();
    const normalizedBrand = marca.trim();

    const priceError = validatePrice(precio);
    const normalizedPrice = Number(precio.trim());

    if (!normalizedName) {
      setValidationMessage("El nombre del producto es obligatorio.");
      return;
    }

    if (priceError) {
      setValidationMessage(priceError);
      return;
    }

    if (selectedCategoryIds.length === 0) {
      setValidationMessage("Selecciona al menos una categoría.");
      return;
    }

    const normalizedVariants = variantes.map((variant) => ({
      id: variant.id,
      nombre: variant.nombre.trim(),
      colorHex: variant.colorHex.trim().toUpperCase(),
    }));

    const hasIncompleteVariant = normalizedVariants.some((variant) => {
      return !variant.nombre || !HEX_COLOR_PATTERN.test(variant.colorHex);
    });

    if (hasIncompleteVariant) {
      setValidationMessage("Cada tono debe tener nombre y un color válido.");
      return;
    }

    const duplicateVariant = normalizedVariants.find((variant, index) => {
      const currentName = variant.nombre.toLowerCase();

      return (
        normalizedVariants.findIndex(
          (candidate) => candidate.nombre.toLowerCase() === currentName,
        ) !== index
      );
    });

    if (duplicateVariant) {
      setValidationMessage(
        `No puede haber tonos duplicados: ${duplicateVariant.nombre}.`,
      );
      return;
    }

    setValidationMessage(null);

    await onSubmit({
      nombre: normalizedName,
      precio: normalizedPrice,
      descripcion: normalizedDescription || null,
      marca: normalizedBrand || null,
      categoriaIds: selectedCategoryIds,
      variantes: normalizedVariants,
    });
  }

  return (
    <main className="page admin-page">
      <section className="admin-hero">
        <div>
          <p className="section-heading__eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>

        <button
          className="secondary-button"
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Volver a productos
        </button>
      </section>

      <section className="admin-form-card">
        <form className="admin-product-form" onSubmit={handleSubmit}>
          <div className="admin-form-grid admin-form-grid--two">
            <label className="form-field">
              <span>Nombre</span>
              <input
                type="text"
                value={nombre}
                onChange={(event) => setNombre(event.target.value)}
                maxLength={160}
                required
                placeholder="Ej: Labial negro mate"
              />
            </label>

            <label className="form-field">
              <span>Precio</span>
              <input
                type="number"
                value={precio}
                onChange={(event) => setPrecio(event.target.value)}
                min={0}
                max={MAX_PRICE_COP}
                step={1}
                required
                onInvalid={(event) => {
                  event.preventDefault();
                  setValidationMessage(
                    event.currentTarget.validity.badInput
                      ? "Ingresa un precio válido en pesos enteros."
                      : validatePrice(precio),
                  );
                }}
                placeholder="Ej: 25000"
              />
            </label>
          </div>

          <div className="admin-form-grid admin-form-grid--two">
            <label className="form-field">
              <span>Marca</span>
              <input
                type="text"
                value={marca}
                onChange={(event) => setMarca(event.target.value)}
                maxLength={100}
                placeholder="Ej: Hajuvi"
              />
            </label>
          </div>

          <label className="form-field">
            <span>Categorías</span>
            <div className="admin-category-picker admin-category-picker--wide">
              {categories.length === 0 ? (
                <span className="admin-category-picker__empty">
                  No hay categorías disponibles.
                </span>
              ) : null}

              {categories.map((category) => (
                <label className="admin-category-picker__option" key={category.id}>
                  <input
                    type="checkbox"
                    checked={selectedCategoryIds.includes(category.id)}
                    onChange={(event) =>
                      handleCategoryChange(category.id, event.target.checked)
                    }
                  />
                  <span>{category.nombre}</span>
                </label>
              ))}
            </div>
          </label>

          <label className="form-field">
            <span>Descripción</span>
            <textarea
              value={descripcion}
              onChange={(event) => setDescripcion(event.target.value)}
              maxLength={3000}
              rows={5}
              placeholder="Describe el producto, beneficios, modo de uso o detalles relevantes."
            />
          </label>

          <section className="admin-variants-editor">
            <div className="admin-variants-editor__header">
              <div>
                <h2>Tonos / colores</h2>
                <p>
                  Agrega tonos solo si el producto tiene variantes
                  seleccionables, como labiales, rubores o productos disponibles
                  en varios colores.
                </p>
              </div>

              <button
                className="secondary-button secondary-button--small"
                type="button"
                onClick={handleAddVariant}
              >
                <span aria-hidden="true">+</span> Tono
              </button>
            </div>

            {variantes.length === 0 ? (
              <div className="state-box">
                Este producto no tiene tonos configurados.
              </div>
            ) : null}

            {variantes.length > 0 ? (
              <div className="admin-variants-list">
                {variantes.map((variant, index) => (
                  <article
                    className="admin-variant-row"
                    key={variant.id ?? `new-${index}`}
                  >
                    <label className="form-field">
                      <span>Nombre del tono</span>
                      <input
                        type="text"
                        value={variant.nombre}
                        onChange={(event) =>
                          handleVariantNameChange(index, event.target.value)
                        }
                        maxLength={80}
                        placeholder="Ej: Moka, Rosa nude, Rojo cereza"
                      />
                    </label>

                    <label className="form-field admin-variant-color-field">
                      <span>Color</span>
                      <div className="admin-variant-color-control">
                        <input
                          type="color"
                          value={
                            HEX_COLOR_PATTERN.test(variant.colorHex)
                              ? variant.colorHex
                              : "#C08A7A"
                          }
                          onChange={(event) =>
                            handleVariantColorChange(index, event.target.value)
                          }
                          aria-label={`Color del tono ${
                            variant.nombre || index + 1
                          }`}
                        />

                        <input
                          type="text"
                          value={variant.colorHex}
                          onChange={(event) =>
                            handleVariantColorChange(index, event.target.value)
                          }
                          maxLength={7}
                          placeholder="#C08A7A"
                        />
                      </div>
                    </label>

                    <button
                      className="secondary-button secondary-button--small admin-variant-row__remove"
                      type="button"
                      onClick={() => handleRemoveVariant(index)}
                    >
                      Quitar
                    </button>
                  </article>
                ))}
              </div>
            ) : null}
          </section>

          {successMessage ? (
            <div className="form-message form-message--success">
              {successMessage}
            </div>
          ) : null}

          {validationMessage ? (
            <div className="form-message form-message--error">
              {validationMessage}
            </div>
          ) : null}

          {errorMessage ? (
            <div className="form-message form-message--error">
              {errorMessage}
            </div>
          ) : null}

          <div className="admin-form-actions">
            <button
              className="secondary-button"
              type="button"
              onClick={onCancel}
              disabled={isSubmitting}
            >
              Cancelar
            </button>

            <button
              className="primary-button"
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Guardando..." : submitLabel}
            </button>
          </div>
        </form>
      </section>

      {afterContent}
    </main>
  );
}
