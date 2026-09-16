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
};

export type CartItem = {
  productId: string;
  name: string;
  variant: string;
  quantity: number;
  price: number;
};

export type User = {
  id: string;
  email: string;
  name: string;
  password?: string;
  role?: "CUSTOMER" | "ADMIN";
};

export type OrderItem = {
  productId: string;
  quantity: number;
  price: number;
};

export type OrderRecord = {
  id: number;
  userId: string;
  fecha: string;
  items: OrderItem[];
  total: number;
  estado: string;
};

export type ReturnRecord = {
  id: number;
  userId: string;
  productId: string;
  cantidad: number;
  motivo: string;
  estado: "PENDIENTE" | "APROBADA" | "RECHAZADA" | "PROCESADO";
  requestedAt: string;
};

export type View = "home" | "products" | "offers" | "categories" | "cart" | "login" | "admin";
