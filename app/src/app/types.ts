export type Category = {
  id: string;
  name: string;
  color?: string;
};

export type Product = {
  id: string;
  name: string;
  brand: string;
  price: number;
  oldPrice?: number | null;
  rating: number;
  imageUrl?: string;
  badge?: string;
  categoryId: string;
  stock: number;
  precioPromocional?: number | null;
  tipoPromocion?: string | null;
  hasVariants?: boolean;
};

export type ProductVariant = {
  id: number;
  productId: string;
  talle?: string | null;
  color?: string | null;
  stock: number;
  imageUrl?: string | null;
};

export type CartItem = {
  productId: string;
  name: string;
  variant: string;
  variantId?: number | null;
  quantity: number;
  price: number;
};

export type User = {
  id: string;
  email: string;
  name: string;
  password?: string;
  role?: "CUSTOMER" | "ADMIN";
  active?: boolean;
};

export type OrderItem = {
  productId: string;
  quantity: number;
  price: number;
  variant?: string | null;
  variantId?: number | null;
};

export type OrderRecord = {
  id: number;
  userId: string;
  fecha: string;
  items: OrderItem[];
  subtotal?: number;
  shippingCost?: number;
  discountAmount?: number;
  couponCode?: string | null;
  total: number;
  estado: string;
};

export type TopProductStat = {
  productId: string;
  name: string;
  totalQuantity: number;
};

export type OrderStats = {
  totalOrders: number;
  totalRevenue: number;
  ordersToday: number;
  topProducts: TopProductStat[];
};

export type ReturnRecord = {
  id: number;
  userId: string;
  productId: string;
  variantId?: number | null;
  variant?: string | null;
  cantidad: number;
  motivo: string;
  estado: "PENDIENTE" | "APROBADA" | "RECHAZADA" | "PROCESADO";
  requestedAt: string;
};

export type Review = {
  id: number;
  productId: string;
  authorName: string;
  rating: number;
  comment: string;
  createdAt: string;
};

export type ShippingInfo = {
  nombre: string;
  direccion: string;
  ciudad: string;
  codigoPostal: string;
  telefono: string;
};

export type CheckoutResult = {
  ok: boolean;
  items: CartItem[];
  subtotal: number;
  shippingCost: number;
  discountAmount: number;
  couponCode: string | null;
  total: number;
  orderId: number | null;
  estado?: string;
  estadoPago?: string;
  ruletaDescuento?: number;
};

export type Coupon = {
  id: number;
  code: string;
  discountType: "PERCENTAGE" | "FIXED";
  discountValue: number;
  active: boolean;
  minPurchase: number;
  maxUses: number | null;
  usesCount: number;
  expiresAt: string | null;
};

export type SiteSettings = {
  topbarItems: string[];
  announcementMode: "AUTO" | "CUSTOM" | "OFF";
  announcementText: string;
};

/** Envío gratis y regalo del carrito, configurables desde el panel de administración. */
export type CartPromos = {
  freeShippingEnabled: boolean;
  freeShippingThreshold: number;
  giftEnabled: boolean;
  giftMinAmount: number;
  giftProductId: string | null;
  giftProductName?: string | null;
  giftProductImageUrl?: string | null;
  /** El producto de regalo existe, no tiene variantes y tiene stock: si no, no se ofrece. */
  giftAvailable: boolean;
};

export const NO_CART_PROMOS: CartPromos = {
  freeShippingEnabled: false,
  freeShippingThreshold: 0,
  giftEnabled: false,
  giftMinAmount: 0,
  giftProductId: null,
  giftAvailable: false,
};

export type RouletteCategory = { id: string; name: string; color: string };

export type RouletteState = {
  percent: number;
  categories: RouletteCategory[];
  spun: boolean;
  categoryId: string | null;
  categoryName: string | null;
  validUntil: string | null;
  alreadySpun?: boolean;
};

export type RouletteCartDiscount = { percent: number; amount: number; categoryId: string | null; categoryName: string | null };

export type View =
  | "home"
  | "products"
  | "offers"
  | "categories"
  | "cart"
  | "login"
  | "admin"
  | "orders"
  | "wishlist"
  | "account"
  | "confirmation"
  | "terms"
  | "privacy"
  | "contact";
