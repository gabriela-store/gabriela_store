import { apiRequest } from "./http";

export type AdminProduct = {
  id: number;
  nombre: string;
  precio: number;
  descripcion: string;
  slug: string;
  activo: boolean;
  marca: string | null;
  imagenPrincipalUrl: string | null;
  imagenes: AdminProductImage[];
  variantes: AdminProductVariant[];
};

export type AdminProductDetail = AdminProduct & {
  categorias: Array<{
    id: number;
    nombre: string;
    slug: string;
  }>;
  imagenes: Array<{
    id: number;
    url: string;
    altText: string | null;
    principal: boolean;
    orden: number;
  }>;
};

export type AdminProductVariant = {
  id: number;
  nombre: string;
  colorHex: string;
  orden: number;
  activo: boolean;
};

export type AdminProductVariantRequest = {
  id?: number;
  nombre: string;
  colorHex: string;
};

export function getAdminProducts(): Promise<AdminProduct[]> {
  return apiRequest<AdminProduct[]>("/api/admin/products", {
    authenticated: true,
    authMode: "admin",
  });
}

export function getAdminProductById(
  productId: number,
): Promise<AdminProductDetail> {
  return apiRequest<AdminProductDetail>(`/api/admin/products/${productId}`, {
    authenticated: true,
    authMode: "admin",
  });
}

export function activateAdminProduct(
  productId: number,
): Promise<AdminProductDetail> {
  return apiRequest<AdminProductDetail>(
    `/api/admin/products/${productId}/activate`,
    {
      method: "PATCH",
      authenticated: true,
      authMode: "admin",
    },
  );
}

export function deactivateAdminProduct(
  productId: number,
): Promise<AdminProductDetail> {
  return apiRequest<AdminProductDetail>(
    `/api/admin/products/${productId}/deactivate`,
    {
      method: "PATCH",
      authenticated: true,
      authMode: "admin",
    },
  );
}

export type AdminProductUpsertRequest = {
  nombre: string;
  precio: number;
  descripcion: string | null;
  marca: string | null;
  categoriaIds: number[];
  variantes?: AdminProductVariantRequest[];
};

export function createAdminProduct(
  request: AdminProductUpsertRequest,
): Promise<AdminProductDetail> {
  return apiRequest<AdminProductDetail>("/api/admin/products", {
    method: "POST",
    authenticated: true,
    authMode: "admin",
    body: JSON.stringify(request),
  });
}

export function updateAdminProduct(
  productId: number,
  request: AdminProductUpsertRequest,
): Promise<AdminProductDetail> {
  return apiRequest<AdminProductDetail>(`/api/admin/products/${productId}`, {
    method: "PUT",
    authenticated: true,
    authMode: "admin",
    body: JSON.stringify(request),
  });
}

export type AdminProductImage = {
  id: number;
  url: string;
  orden: number;
  principal: boolean;
  altText: string | null;
};

export type AdminProductImageUploadRequest = {
  file: File;
  altText: string | null;
  principal: boolean;
  orden?: number | null;
};

export function uploadAdminProductImage(
  productId: number,
  request: AdminProductImageUploadRequest,
): Promise<AdminProductImage> {
  const formData = new FormData();

  formData.append("file", request.file);

  if (request.altText) {
    formData.append("altText", request.altText);
  }

  formData.append("principal", String(request.principal));

  if (request.orden !== undefined && request.orden !== null) {
    formData.append("orden", String(request.orden));
  }

  return apiRequest<AdminProductImage>(
    `/api/admin/products/${productId}/images/upload`,
    {
      method: "POST",
      authenticated: true,
      authMode: "admin",
      body: formData,
    },
  );
}

export function deleteAdminProductImage(
  productId: number,
  imageId: number,
): Promise<void> {
  return apiRequest<void>(
    `/api/admin/products/${productId}/images/${imageId}`,
    {
      method: "DELETE",
      authenticated: true,
      authMode: "admin",
    },
  );
}

export function markAdminProductImageAsMain(
  productId: number,
  imageId: number,
): Promise<AdminProductImage> {
  return apiRequest<AdminProductImage>(
    `/api/admin/products/${productId}/images/${imageId}/main`,
    {
      method: "PATCH",
      authenticated: true,
      authMode: "admin",
    },
  );
}

export type AdminProductAuditLog = {
  id: number;
  productoId: number;
  productoNombre: string;
  adminId: number;
  adminEmail: string;
  adminNombre: string;
  accion: string;
  detalle: string | null;
  fechaEvento: string;
};

export function getAdminProductAudit(
  productId: number,
  limit = 20,
): Promise<AdminProductAuditLog[]> {
  return apiRequest<AdminProductAuditLog[]>(
    `/api/admin/products/${productId}/audit?limit=${limit}`,
    {
      authenticated: true,
      authMode: "admin",
    },
  );
}

export function duplicateAdminProduct(
  productId: number,
): Promise<AdminProductDetail> {
  return apiRequest<AdminProductDetail>(
    `/api/admin/products/${productId}/duplicate`,
    {
      method: "POST",
      authenticated: true,
      authMode: "admin",
    },
  );
}
