import type { CartItem, Category, CheckoutResult, Coupon, OrderRecord, OrderStats, Product, ProductVariant, ReturnRecord, Review, ShippingInfo, User } from "./types";

const API_BASE_URL = "http://localhost:8080/api";
const API_ORIGIN = API_BASE_URL.replace(/\/api\/?$/, "");
export const AUTH_TOKEN_KEY = "petshop_auth_token";
export const AUTH_USER_KEY = "petshop_auth_user";
export const GUEST_ID_KEY = "petshop_guest_id";
export const UNAUTHORIZED_EVENT = "petshop:unauthorized";

async function request<T>(input: string, init?: (RequestInit & { skipAuthRedirect?: boolean })): Promise<T> {
  const headers = new Headers(init?.headers ?? {});
  if (!headers.has("Content-Type") && !(init?.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${input}`, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const text = await response.text();
    let message = text || "Request failed";
    try {
      const parsed = JSON.parse(text) as { error?: string; detail?: string };
      if (parsed.error) message = parsed.error;
      else if (parsed.detail) message = parsed.detail;
    } catch {
      // not JSON, keep raw text
    }
    if (response.status === 401 && !init?.skipAuthRedirect) {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    throw new Error(message);
  }

  return (await response.json()) as T;
}

export function getGuestId(): string {
  try {
    let id = localStorage.getItem(GUEST_ID_KEY);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(GUEST_ID_KEY, id);
    }
    return id;
  } catch {
    return "unknown";
  }
}

export function getAuthToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

export function getCurrentUser(): User | null {
  const raw = localStorage.getItem(AUTH_USER_KEY);
  return raw ? (JSON.parse(raw) as User) : null;
}

export async function fetchCategories(): Promise<Category[]> {
  return request<Category[]>("/categories");
}

export async function createCategory(token: string, category: Partial<Category>): Promise<Category> {
  return request<Category>("/categories", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(category),
  });
}

export async function updateCategory(token: string, id: string, category: Partial<Category>): Promise<Category> {
  return request<Category>(`/categories/${id}`, {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(category),
  });
}

export async function deleteCategory(token: string, id: string): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>(`/categories/${id}`, {
    method: "DELETE",
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchProducts(category?: string, sort?: string, search?: string): Promise<Product[]> {
  const params = new URLSearchParams();
  if (category && category !== "all") params.set("category", category);
  if (sort) params.set("sort", sort);
  if (search && search.trim()) params.set("search", search.trim());
  const query = params.toString();
  return request<Product[]>(`/products${query ? `?${query}` : ""}`);
}

export async function fetchProduct(id: string): Promise<Product> {
  return request<Product>(`/products/${id}`);
}

export async function fetchProductReviews(productId: string): Promise<Review[]> {
  return request<Review[]>(`/products/${productId}/reviews`);
}

export async function fetchProductVariants(productId: string): Promise<ProductVariant[]> {
  return request<ProductVariant[]>(`/products/${productId}/variants`);
}

export async function createProductVariant(
  token: string,
  productId: string,
  variant: Partial<ProductVariant>,
): Promise<ProductVariant> {
  return request<ProductVariant>(`/products/${productId}/variants`, {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(variant),
  });
}

export async function updateProductVariant(
  token: string,
  productId: string,
  variantId: number,
  variant: Partial<ProductVariant>,
): Promise<ProductVariant> {
  return request<ProductVariant>(`/products/${productId}/variants/${variantId}`, {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(variant),
  });
}

export async function deleteProductVariant(token: string, productId: string, variantId: number): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>(`/products/${productId}/variants/${variantId}`, {
    method: "DELETE",
    headers: { "X-Auth-Token": token },
  });
}

export async function submitReview(token: string, productId: string, rating: number, comment: string): Promise<Review> {
  return request<Review>(`/products/${productId}/reviews`, {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ rating, comment }),
  });
}

export async function deleteReview(token: string, productId: string, reviewId: number): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>(`/products/${productId}/reviews/${reviewId}`, {
    method: "DELETE",
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchWishlist(token: string): Promise<string[]> {
  return request<string[]>("/wishlist", {
    headers: { "X-Auth-Token": token },
  });
}

export async function addToWishlist(token: string, productId: string): Promise<string[]> {
  return request<string[]>("/wishlist", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ productId }),
  });
}

export async function removeFromWishlist(token: string, productId: string): Promise<string[]> {
  return request<string[]>(`/wishlist/${productId}`, {
    method: "DELETE",
    headers: { "X-Auth-Token": token },
  });
}

export async function login(email: string, password: string): Promise<{ token: string; user: User }> {
  return request<{ token: string; user: User }>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
    skipAuthRedirect: true,
  });
}

export async function register(email: string, password: string, name: string): Promise<{ token: string; user: User }> {
  return request<{ token: string; user: User }>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password, name }),
  });
}

export async function recoverPassword(email: string): Promise<{ resetToken: string; expiresInMinutes: number }> {
  return request<{ resetToken: string; expiresInMinutes: number }>("/auth/recover", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export async function resetPassword(token: string, password: string): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>("/auth/reset", {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}

export async function fetchCurrentUser(token: string): Promise<User> {
  return request<User>("/auth/me", {
    headers: {
      "X-Auth-Token": token,
    },
  });
}

export async function updateMyProfile(token: string, name: string): Promise<User> {
  return request<User>("/auth/me", {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ name }),
  });
}

export async function changePassword(token: string, currentPassword: string, newPassword: string): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>("/auth/change-password", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ currentPassword, newPassword }),
    skipAuthRedirect: true,
  });
}

function cartHeaders(token?: string | null): Record<string, string> {
  const headers: Record<string, string> = { "X-Guest-Id": getGuestId() };
  if (token) headers["X-Auth-Token"] = token;
  return headers;
}

export async function fetchCart(token?: string | null): Promise<CartItem[]> {
  return request<CartItem[]>("/cart", {
    headers: cartHeaders(token),
  });
}

export async function addToCart(token: string | null, item: CartItem): Promise<CartItem[]> {
  return request<CartItem[]>("/cart/add", {
    method: "POST",
    headers: cartHeaders(token),
    body: JSON.stringify(item),
  });
}

export async function removeFromCart(token: string | null, item: CartItem): Promise<CartItem[]> {
  return request<CartItem[]>("/cart/remove", {
    method: "POST",
    headers: cartHeaders(token),
    body: JSON.stringify(item),
  });
}

export async function incrementCartItem(token: string | null, productId: string, variantId?: number | null): Promise<CartItem[]> {
  const query = variantId != null ? `?variantId=${variantId}` : "";
  return request<CartItem[]>(`/cart/items/${productId}/increment${query}`, {
    method: "PUT",
    headers: cartHeaders(token),
  });
}

export async function decrementCartItem(token: string | null, productId: string, variantId?: number | null): Promise<CartItem[]> {
  const query = variantId != null ? `?variantId=${variantId}` : "";
  return request<CartItem[]>(`/cart/items/${productId}/decrement${query}`, {
    method: "PUT",
    headers: cartHeaders(token),
  });
}

type VentaResponse = {
  id: number;
  estado: string;
  estadoPago: string;
  items: { productId: string; name: string; variant: string; variantId: number | null; quantity: number; price: number }[];
  subtotal: number;
  shippingCost: number;
  discountAmount: number;
  couponCode: string | null;
  total: number;
};

export async function createVenta(
  token: string | null,
  shipping: Partial<ShippingInfo>,
  couponCode: string | null | undefined,
  medioPago: string,
  idempotencyKey: string,
): Promise<CheckoutResult> {
  const venta = await request<VentaResponse>("/v1/ventas", {
    method: "POST",
    headers: { ...cartHeaders(token), "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ ...shipping, ...(couponCode ? { cupon: couponCode } : {}), medioPago }),
  });

  return {
    ok: true,
    items: venta.items.map((item) => ({
      productId: item.productId,
      name: item.name,
      variant: item.variant,
      variantId: item.variantId,
      quantity: item.quantity,
      price: item.price,
    })),
    subtotal: venta.subtotal,
    shippingCost: venta.shippingCost,
    discountAmount: venta.discountAmount,
    couponCode: venta.couponCode,
    total: venta.total,
    orderId: venta.id,
    estado: venta.estado,
    estadoPago: venta.estadoPago,
  };
}

export async function validateCoupon(code: string, subtotal: number): Promise<{ code: string; discountAmount: number }> {
  return request<{ code: string; discountAmount: number }>("/coupons/validate", {
    method: "POST",
    body: JSON.stringify({ code, subtotal }),
  });
}

export async function fetchCoupons(token: string): Promise<Coupon[]> {
  return request<Coupon[]>("/coupons", {
    headers: { "X-Auth-Token": token },
  });
}

export async function createCoupon(token: string, coupon: Partial<Coupon>): Promise<Coupon> {
  return request<Coupon>("/coupons", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(coupon),
  });
}

export async function updateCoupon(token: string, id: number, coupon: Partial<Coupon>): Promise<Coupon> {
  return request<Coupon>(`/coupons/${id}`, {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(coupon),
  });
}

export async function deleteCoupon(token: string, id: number): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>(`/coupons/${id}`, {
    method: "DELETE",
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchAllUsers(token: string): Promise<User[]> {
  return request<User[]>("/users", {
    headers: { "X-Auth-Token": token },
  });
}

export async function updateUserRole(token: string, userId: string, role: "ADMIN" | "CUSTOMER"): Promise<User> {
  return request<User>(`/users/${userId}/role`, {
    method: "PATCH",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ role }),
  });
}

export async function updateUserStatus(token: string, userId: string, active: boolean): Promise<User> {
  return request<User>(`/users/${userId}/status`, {
    method: "PATCH",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ active }),
  });
}

export async function fetchAllOrders(token: string): Promise<OrderRecord[]> {
  return request<OrderRecord[]>("/orders", {
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchMyOrders(token: string): Promise<OrderRecord[]> {
  return request<OrderRecord[]>("/orders/me", {
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchOrderStats(token: string): Promise<OrderStats> {
  return request<OrderStats>("/orders/stats", {
    headers: { "X-Auth-Token": token },
  });
}

export async function updateOrderStatus(token: string, orderId: number, estado: string): Promise<OrderRecord> {
  return request<OrderRecord>(`/orders/${orderId}/status`, {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ estado }),
  });
}

export async function uploadImage(token: string, file: File): Promise<string> {
  const formData = new FormData();
  formData.append("file", file);
  const result = await request<{ url: string }>("/uploads", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: formData,
  });
  return `${API_ORIGIN}${result.url}`;
}

export async function createProduct(token: string, product: Partial<Product>): Promise<Product> {
  return request<Product>("/products", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(product),
  });
}

export async function updateProduct(token: string, id: string, product: Partial<Product>): Promise<Product> {
  return request<Product>(`/products/${id}`, {
    method: "PUT",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify(product),
  });
}

export async function deleteProduct(token: string, id: string): Promise<{ ok: boolean }> {
  return request<{ ok: boolean }>(`/products/${id}`, {
    method: "DELETE",
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchAllReturns(token: string): Promise<ReturnRecord[]> {
  return request<ReturnRecord[]>("/returns", {
    headers: { "X-Auth-Token": token },
  });
}

export async function fetchMyReturns(token: string): Promise<ReturnRecord[]> {
  return request<ReturnRecord[]>("/returns/me", {
    headers: { "X-Auth-Token": token },
  });
}

export async function requestReturn(
  token: string,
  productId: string,
  cantidad: number,
  motivo: string,
  variantId?: number | null,
  variant?: string | null,
): Promise<ReturnRecord> {
  return request<ReturnRecord>("/returns", {
    method: "POST",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ productId, cantidad, motivo, variantId, variant }),
  });
}

export async function updateReturnStatus(
  token: string,
  returnId: number,
  estado: "APROBADA" | "RECHAZADA",
): Promise<ReturnRecord> {
  return request<ReturnRecord>(`/returns/${returnId}/status`, {
    method: "PATCH",
    headers: { "X-Auth-Token": token },
    body: JSON.stringify({ estado }),
  });
}

export async function processRefund(token: string, returnId: number): Promise<{ id: number; returnId: number; monto: number; estado: string }> {
  return request(`/refunds/process/${returnId}`, {
    method: "POST",
    headers: { "X-Auth-Token": token },
  });
}
