import { useEffect, useMemo, useState } from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { Category, Coupon, OrderRecord, OrderStats, Product, ProductVariant, ReturnRecord, SiteSettings, User } from "../types";
import { AnnouncementSettings } from "./AnnouncementSettings";
import {
  createCategory,
  createCoupon,
  createProduct,
  createProductVariant,
  deleteCategory,
  deleteCoupon,
  deleteProduct,
  deleteProductVariant,
  fetchAllOrders,
  fetchAllReturns,
  fetchAllUsers,
  fetchCategories,
  fetchCoupons,
  fetchOrderStats,
  fetchProducts,
  fetchProductVariants,
  processRefund,
  updateCategory,
  updateCoupon,
  updateOrderStatus,
  updateProduct,
  updateProductVariant,
  updateReturnStatus,
  updateUserRole,
  updateUserStatus,
  uploadImage,
} from "../api";

type Props = {
  authToken: string;
  categories: Category[];
  currentUserId: string;
  siteSettings: SiteSettings;
  onSiteSettingsChange: (settings: SiteSettings) => void;
};

type Tab = "stats" | "orders" | "products" | "categories" | "returns" | "users" | "coupons" | "announcements";

const EMPTY_CATEGORY_FORM = { id: "", name: "", color: "#f97316" };

const EMPTY_COUPON_FORM = {
  code: "",
  discountType: "PERCENTAGE" as "PERCENTAGE" | "FIXED",
  discountValue: "",
  minPurchase: "",
  maxUses: "",
  expiresAt: "",
  active: true,
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

const PROMO_TYPES = [
  "2x1",
  "3x2",
  "3x1",
  "2do al 50%",
  "10% OFF",
  "15% OFF",
  "20% OFF",
  "25% OFF",
  "30% OFF",
  "40% OFF",
  "50% OFF",
  "Envío gratis",
];

const EMPTY_VARIANT_FORM = { talle: "", color: "", stock: "", imageUrl: "" };

const EMPTY_PRODUCT_FORM = {
  id: "",
  name: "",
  brand: "",
  price: "",
  oldPrice: "",
  rating: "",
  imageUrl: "",
  badge: "",
  categoryId: "",
  stock: "",
  precioPromocional: "",
  tipoPromocion: "",
};

export function AdminDashboardScreen({ authToken, categories, currentUserId, siteSettings, onSiteSettingsChange }: Props) {
  const [tab, setTab] = useState<Tab>("stats");

  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);

  const [stats, setStats] = useState<OrderStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);

  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [orderStatusFilter, setOrderStatusFilter] = useState("");

  const [products, setProducts] = useState<Product[]>([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productForm, setProductForm] = useState(EMPTY_PRODUCT_FORM);
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [productError, setProductError] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);
  const [variantImageUploading, setVariantImageUploading] = useState(false);

  const [variantList, setVariantList] = useState<ProductVariant[]>([]);
  const [variantsLoading, setVariantsLoading] = useState(false);
  const [variantForm, setVariantForm] = useState(EMPTY_VARIANT_FORM);
  const [editingVariantId, setEditingVariantId] = useState<number | null>(null);
  const [variantError, setVariantError] = useState<string | null>(null);

  const [returns, setReturns] = useState<ReturnRecord[]>([]);
  const [returnsLoading, setReturnsLoading] = useState(false);

  const [categoryList, setCategoryList] = useState<Category[]>(categories);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [categoryForm, setCategoryForm] = useState(EMPTY_CATEGORY_FORM);
  const [editingCategoryId, setEditingCategoryId] = useState<string | null>(null);
  const [categoryError, setCategoryError] = useState<string | null>(null);

  const [couponList, setCouponList] = useState<Coupon[]>([]);
  const [couponsLoading, setCouponsLoading] = useState(false);
  const [couponForm, setCouponForm] = useState(EMPTY_COUPON_FORM);
  const [editingCouponId, setEditingCouponId] = useState<number | null>(null);
  const [couponError, setCouponError] = useState<string | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);

  const loadStats = async () => {
    setStatsLoading(true);
    try {
      const data = await fetchOrderStats(authToken);
      setStats(data);
    } catch (error) {
      console.error("No se pudieron cargar las estadísticas", error);
    } finally {
      setStatsLoading(false);
    }
  };

  const loadOrders = async () => {
    setOrdersLoading(true);
    try {
      const data = await fetchAllOrders(authToken);
      setOrders(data);
    } catch (error) {
      console.error("No se pudieron cargar las órdenes", error);
    } finally {
      setOrdersLoading(false);
    }
  };

  const loadProducts = async () => {
    setProductsLoading(true);
    try {
      const data = await fetchProducts();
      setProducts(data);
    } catch (error) {
      console.error("No se pudieron cargar los productos", error);
    } finally {
      setProductsLoading(false);
    }
  };

  const loadReturns = async () => {
    setReturnsLoading(true);
    try {
      const data = await fetchAllReturns(authToken);
      setReturns(data);
    } catch (error) {
      console.error("No se pudieron cargar las devoluciones", error);
    } finally {
      setReturnsLoading(false);
    }
  };

  const loadCategories = async () => {
    setCategoriesLoading(true);
    try {
      setCategoryList(await fetchCategories());
    } catch (error) {
      console.error("No se pudieron cargar las categorías", error);
    } finally {
      setCategoriesLoading(false);
    }
  };

  const loadUsers = async () => {
    setUsersLoading(true);
    try {
      setUsers(await fetchAllUsers(authToken));
    } catch (error) {
      console.error("No se pudieron cargar los usuarios", error);
    } finally {
      setUsersLoading(false);
    }
  };

  const handleUserRoleChange = async (user: User, role: "ADMIN" | "CUSTOMER") => {
    setActionError(null);
    try {
      const updated = await updateUserRole(authToken, user.id, role);
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo cambiar el rol del usuario");
    }
  };

  const handleUserStatusChange = async (user: User, active: boolean) => {
    setActionError(null);
    try {
      const updated = await updateUserStatus(authToken, user.id, active);
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo cambiar el estado del usuario");
    }
  };

  const loadCoupons = async () => {
    setCouponsLoading(true);
    try {
      setCouponList(await fetchCoupons(authToken));
    } catch (error) {
      console.error("No se pudieron cargar los cupones", error);
    } finally {
      setCouponsLoading(false);
    }
  };

  const startEditCoupon = (coupon: Coupon) => {
    setEditingCouponId(coupon.id);
    setCouponForm({
      code: coupon.code,
      discountType: coupon.discountType,
      discountValue: String(coupon.discountValue),
      minPurchase: coupon.minPurchase ? String(coupon.minPurchase) : "",
      maxUses: coupon.maxUses != null ? String(coupon.maxUses) : "",
      expiresAt: coupon.expiresAt ? coupon.expiresAt.slice(0, 10) : "",
      active: coupon.active,
    });
    setCouponError(null);
  };

  const resetCouponForm = () => {
    setEditingCouponId(null);
    setCouponForm(EMPTY_COUPON_FORM);
    setCouponError(null);
  };

  // Al elegir "N% OFF" y no haber precio promocional, lo calcula a partir del precio (se puede editar después).
  const handlePromoTypeChange = (tipoPromocion: string) => {
    const percent = /^(\d+)% OFF$/.exec(tipoPromocion);
    const price = Number(productForm.price);
    if (percent && !productForm.precioPromocional && price > 0) {
      const discounted = Math.round(price * (1 - Number(percent[1]) / 100) * 100) / 100;
      setProductForm({ ...productForm, tipoPromocion, precioPromocional: String(discounted) });
      return;
    }
    setProductForm({ ...productForm, tipoPromocion });
  };

  const handleCouponSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCouponError(null);

    if (!couponForm.code.trim() || !couponForm.discountValue) {
      setCouponError("Completa al menos el código y el valor del descuento.");
      return;
    }

    const payload: Partial<Coupon> = {
      code: couponForm.code.trim(),
      discountType: couponForm.discountType,
      discountValue: Number(couponForm.discountValue),
      minPurchase: couponForm.minPurchase ? Number(couponForm.minPurchase) : 0,
      maxUses: couponForm.maxUses ? Number(couponForm.maxUses) : null,
      expiresAt: couponForm.expiresAt ? new Date(couponForm.expiresAt).toISOString() : null,
      active: couponForm.active,
    };

    try {
      if (editingCouponId) {
        await updateCoupon(authToken, editingCouponId, payload);
      } else {
        await createCoupon(authToken, payload);
      }
      resetCouponForm();
      await loadCoupons();
    } catch (error) {
      setCouponError(error instanceof Error ? error.message : "No se pudo guardar el cupón");
    }
  };

  const handleDeleteCoupon = async (coupon: Coupon) => {
    setActionError(null);
    try {
      await deleteCoupon(authToken, coupon.id);
      await loadCoupons();
      if (editingCouponId === coupon.id) {
        resetCouponForm();
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo eliminar el cupón");
    }
  };

  useEffect(() => {
    void loadStats();
    void loadOrders();
    void loadProducts();
    void loadReturns();
    void loadCategories();
    void loadUsers();
    void loadCoupons();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleOrderStatusChange = async (order: OrderRecord, estado: string) => {
    setActionError(null);
    try {
      const updated = await updateOrderStatus(authToken, order.id, estado);
      setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo cambiar el estado de la orden");
    }
  };

  const filteredOrders = orderStatusFilter
    ? orders.filter((order) => order.estado === orderStatusFilter)
    : orders;

  const orderStatuses = Array.from(new Set(orders.map((o) => o.estado)));

  const [statsFrom, setStatsFrom] = useState("");
  const [statsTo, setStatsTo] = useState("");

  const statsFilteredOrders = useMemo(() => {
    return orders.filter((order) => {
      const day = new Date(order.fecha).toISOString().slice(0, 10);
      if (statsFrom && day < statsFrom) return false;
      if (statsTo && day > statsTo) return false;
      return true;
    });
  }, [orders, statsFrom, statsTo]);

  const filteredTotalOrders = statsFilteredOrders.length;
  const filteredTotalRevenue = statsFilteredOrders.reduce((sum, o) => sum + o.total, 0);

  const filteredTopProducts = useMemo(() => {
    const quantityById = new Map<string, number>();
    statsFilteredOrders.forEach((order) => {
      order.items.forEach((item) => {
        quantityById.set(item.productId, (quantityById.get(item.productId) || 0) + item.quantity);
      });
    });
    const nameById = new Map(products.map((p) => [p.id, p.name]));
    return [...quantityById.entries()]
      .map(([productId, totalQuantity]) => ({
        productId,
        name: nameById.get(productId) || productId,
        totalQuantity,
      }))
      .sort((a, b) => b.totalQuantity - a.totalQuantity)
      .slice(0, 5);
  }, [statsFilteredOrders, products]);

  const revenueByDay = useMemo(() => {
    let start: Date;
    let end: Date;
    if (statsFrom || statsTo) {
      start = new Date(statsFrom || statsTo);
      end = new Date(statsTo || statsFrom);
    } else {
      end = new Date();
      start = new Date();
      start.setDate(start.getDate() - 6);
    }

    const days: { date: string; label: string; total: number }[] = [];
    const cursor = new Date(start);
    const MAX_DAYS = 60;
    while (cursor <= end && days.length < MAX_DAYS) {
      const key = cursor.toISOString().slice(0, 10);
      days.push({ date: key, label: cursor.toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit" }), total: 0 });
      cursor.setDate(cursor.getDate() + 1);
    }
    const byDate = new Map(days.map((d) => [d.date, d]));
    statsFilteredOrders.forEach((order) => {
      const key = new Date(order.fecha).toISOString().slice(0, 10);
      const entry = byDate.get(key);
      if (entry) {
        entry.total += order.total;
      }
    });
    return days;
  }, [statsFilteredOrders, statsFrom, statsTo]);

  const handleExportCsv = () => {
    const headers = ["ID", "Fecha", "Usuario", "Items", "Subtotal", "Envio", "Descuento", "Cupon", "Total", "Estado"];
    const rows = statsFilteredOrders.map((o) => [
      o.id,
      new Date(o.fecha).toLocaleString("es-AR"),
      o.userId,
      o.items.reduce((sum, i) => sum + i.quantity, 0),
      o.subtotal ?? "",
      o.shippingCost ?? "",
      o.discountAmount ?? "",
      o.couponCode ?? "",
      o.total,
      o.estado,
    ]);
    const csv = [headers, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(","))
      .join("\n");
    const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `ordenes_${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const ORDERS_PAGE_SIZE = 10;
  const [orderPage, setOrderPage] = useState(1);

  useEffect(() => {
    setOrderPage(1);
  }, [orderStatusFilter, orders.length]);

  const orderTotalPages = Math.max(1, Math.ceil(filteredOrders.length / ORDERS_PAGE_SIZE));
  const orderCurrentPage = Math.min(orderPage, orderTotalPages);
  const paginatedOrders = filteredOrders.slice(
    (orderCurrentPage - 1) * ORDERS_PAGE_SIZE,
    orderCurrentPage * ORDERS_PAGE_SIZE,
  );

  const loadVariants = async (productId: string) => {
    setVariantsLoading(true);
    try {
      setVariantList(await fetchProductVariants(productId));
    } catch (error) {
      console.error("No se pudieron cargar las variantes", error);
    } finally {
      setVariantsLoading(false);
    }
  };

  const startEditProduct = (product: Product) => {
    setEditingProductId(product.id);
    setProductForm({
      id: product.id,
      name: product.name,
      brand: product.brand,
      price: String(product.price),
      oldPrice: product.oldPrice != null ? String(product.oldPrice) : "",
      rating: String(product.rating),
      imageUrl: product.imageUrl || "",
      badge: product.badge || "",
      categoryId: product.categoryId,
      stock: String(product.stock),
      precioPromocional: product.precioPromocional != null ? String(product.precioPromocional) : "",
      tipoPromocion: product.tipoPromocion || "",
    });
    setProductError(null);
    resetVariantForm();
    void loadVariants(product.id);
  };

  const resetProductForm = () => {
    setEditingProductId(null);
    setProductForm(EMPTY_PRODUCT_FORM);
    setProductError(null);
    setVariantList([]);
    resetVariantForm();
  };

  const resetVariantForm = () => {
    setEditingVariantId(null);
    setVariantForm(EMPTY_VARIANT_FORM);
    setVariantError(null);
  };

  const startEditVariant = (variant: ProductVariant) => {
    setEditingVariantId(variant.id);
    setVariantForm({
      talle: variant.talle || "",
      color: variant.color || "",
      stock: String(variant.stock),
      imageUrl: variant.imageUrl || "",
    });
    setVariantError(null);
  };

  const handleVariantSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingProductId) return;
    setVariantError(null);

    if (!variantForm.talle.trim() && !variantForm.color.trim()) {
      setVariantError("Indicá al menos talle o color.");
      return;
    }

    const payload: Partial<ProductVariant> = {
      talle: variantForm.talle.trim() || null,
      color: variantForm.color.trim() || null,
      stock: variantForm.stock ? Number(variantForm.stock) : 0,
      imageUrl: variantForm.imageUrl.trim() || null,
    };

    try {
      if (editingVariantId) {
        await updateProductVariant(authToken, editingProductId, editingVariantId, payload);
      } else {
        await createProductVariant(authToken, editingProductId, payload);
      }
      resetVariantForm();
      await loadVariants(editingProductId);
      await loadProducts();
    } catch (error) {
      setVariantError(error instanceof Error ? error.message : "No se pudo guardar la variante");
    }
  };

  const handleDeleteVariant = async (variant: ProductVariant) => {
    if (!editingProductId) return;
    setVariantError(null);
    try {
      await deleteProductVariant(authToken, editingProductId, variant.id);
      await loadVariants(editingProductId);
      await loadProducts();
      if (editingVariantId === variant.id) {
        resetVariantForm();
      }
    } catch (error) {
      setVariantError(error instanceof Error ? error.message : "No se pudo eliminar la variante");
    }
  };

  const handleProductSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProductError(null);

    if (!productForm.name.trim() || !productForm.price || !productForm.categoryId) {
      setProductError("Completa al menos nombre, precio y categoría.");
      return;
    }

    const payload: Partial<Product> = {
      id: productForm.id.trim() || undefined,
      name: productForm.name.trim(),
      brand: productForm.brand.trim(),
      price: Number(productForm.price),
      oldPrice: productForm.oldPrice ? Number(productForm.oldPrice) : null,
      rating: productForm.rating ? Number(productForm.rating) : 0,
      imageUrl: productForm.imageUrl.trim(),
      badge: productForm.badge.trim() || undefined,
      categoryId: productForm.categoryId,
      stock: productForm.stock ? Number(productForm.stock) : 0,
      precioPromocional: productForm.precioPromocional ? Number(productForm.precioPromocional) : null,
      tipoPromocion: productForm.tipoPromocion.trim() || null,
    };

    try {
      if (editingProductId) {
        await updateProduct(authToken, editingProductId, payload);
      } else {
        await createProduct(authToken, payload);
      }
      resetProductForm();
      await loadProducts();
    } catch (error) {
      setProductError(error instanceof Error ? error.message : "No se pudo guardar el producto");
    }
  };

  const handleDeleteProduct = async (product: Product) => {
    setActionError(null);
    try {
      await deleteProduct(authToken, product.id);
      await loadProducts();
      if (editingProductId === product.id) {
        resetProductForm();
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo eliminar el producto");
    }
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setProductError(null);
    setImageUploading(true);
    try {
      const url = await uploadImage(authToken, file);
      setProductForm((prev) => ({ ...prev, imageUrl: url }));
    } catch (error) {
      setProductError(error instanceof Error ? error.message : "No se pudo subir la imagen");
    } finally {
      setImageUploading(false);
      e.target.value = "";
    }
  };

  const handleVariantImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setVariantError(null);
    setVariantImageUploading(true);
    try {
      const url = await uploadImage(authToken, file);
      setVariantForm((prev) => ({ ...prev, imageUrl: url }));
    } catch (error) {
      setVariantError(error instanceof Error ? error.message : "No se pudo subir la imagen");
    } finally {
      setVariantImageUploading(false);
      e.target.value = "";
    }
  };

  const startEditCategory = (category: Category) => {
    setEditingCategoryId(category.id);
    setCategoryForm({ id: category.id, name: category.name, color: category.color || "#f97316" });
    setCategoryError(null);
  };

  const resetCategoryForm = () => {
    setEditingCategoryId(null);
    setCategoryForm(EMPTY_CATEGORY_FORM);
    setCategoryError(null);
  };

  const handleCategorySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCategoryError(null);

    if (!categoryForm.name.trim()) {
      setCategoryError("Completa el nombre de la categoría.");
      return;
    }

    const payload: Partial<Category> = {
      id: categoryForm.id.trim() || undefined,
      name: categoryForm.name.trim(),
      color: categoryForm.color,
    };

    try {
      if (editingCategoryId) {
        await updateCategory(authToken, editingCategoryId, payload);
      } else {
        await createCategory(authToken, payload);
      }
      resetCategoryForm();
      await loadCategories();
    } catch (error) {
      setCategoryError(error instanceof Error ? error.message : "No se pudo guardar la categoría");
    }
  };

  const handleDeleteCategory = async (category: Category) => {
    setActionError(null);
    try {
      await deleteCategory(authToken, category.id);
      await loadCategories();
      if (editingCategoryId === category.id) {
        resetCategoryForm();
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo eliminar la categoría");
    }
  };

  const handleReturnDecision = async (devolucion: ReturnRecord, estado: "APROBADA" | "RECHAZADA") => {
    setActionError(null);
    try {
      const updated = await updateReturnStatus(authToken, devolucion.id, estado);
      setReturns((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo actualizar la devolución");
    }
  };

  const handleProcessRefund = async (devolucion: ReturnRecord) => {
    setActionError(null);
    try {
      await processRefund(authToken, devolucion.id);
      await loadReturns();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo procesar el reembolso");
    }
  };

  const pendingReturns = returns.filter((r) => r.estado === "PENDIENTE");
  const approvedReturns = returns.filter((r) => r.estado === "APROBADA");

  return (
    <div className="page-shell admin-shell">
      <div className="section-header">
        <h2>Panel de administración</h2>
      </div>

      <div className="admin-tabs">
        <button className={tab === "stats" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("stats")}>
          Estadísticas
        </button>
        <button className={tab === "orders" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("orders")}>
          Órdenes
        </button>
        <button className={tab === "products" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("products")}>
          Productos
        </button>
        <button className={tab === "categories" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("categories")}>
          Categorías
        </button>
        <button className={tab === "returns" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("returns")}>
          Devoluciones {pendingReturns.length + approvedReturns.length > 0 ? `(${pendingReturns.length + approvedReturns.length})` : ""}
        </button>
        <button className={tab === "users" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("users")}>
          Usuarios
        </button>
        <button className={tab === "coupons" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("coupons")}>
          Cupones
        </button>
        <button className={tab === "announcements" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("announcements")}>
          Anuncios
        </button>
      </div>

      {actionError ? <div className="error-box">{actionError}</div> : null}

      {tab === "stats" ? (
        <section className="admin-section">
          {statsLoading || !stats ? (
            <div className="empty-state">Cargando estadísticas...</div>
          ) : (
            <>
              <div className="toolbar-card">
                <div className="toolbar-row">
                  <label>
                    <span>Desde</span>
                    <input type="date" value={statsFrom} onChange={(e) => setStatsFrom(e.target.value)} />
                  </label>
                  <label>
                    <span>Hasta</span>
                    <input type="date" value={statsTo} onChange={(e) => setStatsTo(e.target.value)} />
                  </label>
                  {statsFrom || statsTo ? (
                    <button
                      className="secondary-btn"
                      type="button"
                      onClick={() => {
                        setStatsFrom("");
                        setStatsTo("");
                      }}
                    >
                      Limpiar filtro
                    </button>
                  ) : null}
                  <button className="secondary-btn" type="button" onClick={handleExportCsv}>
                    Exportar CSV
                  </button>
                </div>
              </div>

              <div className="stats-grid">
                <div className="stat-card">
                  <span>{statsFrom || statsTo ? "Órdenes en el rango" : "Total de órdenes"}</span>
                  <strong>{filteredTotalOrders}</strong>
                </div>
                <div className="stat-card">
                  <span>{statsFrom || statsTo ? "Ingresos en el rango" : "Ingresos totales"}</span>
                  <strong>{formatMoney(filteredTotalRevenue)}</strong>
                </div>
                <div className="stat-card">
                  <span>Órdenes de hoy</span>
                  <strong>{stats.ordersToday}</strong>
                </div>
              </div>

              <div className="charts-grid">
                <div className="chart-card">
                  <h3>Ingresos {statsFrom || statsTo ? "— rango seleccionado" : "— últimos 7 días"}</h3>
                  <ResponsiveContainer width="100%" height={240}>
                    <AreaChart data={revenueByDay}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#fed7aa" />
                      <XAxis dataKey="label" stroke="#7c2d12" fontSize={12} />
                      <YAxis stroke="#7c2d12" fontSize={12} width={70} />
                      <Tooltip formatter={(value: number) => formatMoney(value)} />
                      <Area type="monotone" dataKey="total" name="Ingresos" stroke="#f97316" fill="#fed7aa" />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>

                <div className="chart-card">
                  <h3>Top productos vendidos</h3>
                  {filteredTopProducts.length === 0 ? (
                    <div className="empty-state">Todavía no hay ventas.</div>
                  ) : (
                    <ResponsiveContainer width="100%" height={240}>
                      <BarChart data={filteredTopProducts} layout="vertical" margin={{ left: 24 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#fed7aa" />
                        <XAxis type="number" stroke="#7c2d12" fontSize={12} allowDecimals={false} />
                        <YAxis type="category" dataKey="name" width={130} stroke="#7c2d12" fontSize={11} />
                        <Tooltip />
                        <Bar dataKey="totalQuantity" name="Unidades" fill="#f97316" radius={[0, 6, 6, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  )}
                </div>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Producto</th>
                      <th>Unidades vendidas</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredTopProducts.length === 0 ? (
                      <tr>
                        <td colSpan={3}>Todavía no hay ventas.</td>
                      </tr>
                    ) : (
                      filteredTopProducts.map((product, index) => (
                        <tr key={product.productId}>
                          <td>{index + 1}</td>
                          <td>{product.name}</td>
                          <td>{product.totalQuantity}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </section>
      ) : null}

      {tab === "orders" ? (
        <section className="admin-section">
          <div className="toolbar-card">
            <div className="toolbar-row">
              <label>
                <span>Filtrar por estado</span>
                <select value={orderStatusFilter} onChange={(e) => setOrderStatusFilter(e.target.value)}>
                  <option value="">Todos</option>
                  {orderStatuses.map((estado) => (
                    <option key={estado} value={estado}>
                      {estado}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </div>

          {ordersLoading ? (
            <div className="empty-state">Cargando órdenes...</div>
          ) : filteredOrders.length === 0 ? (
            <div className="empty-state">No hay órdenes para mostrar.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Usuario</th>
                    <th>Fecha</th>
                    <th>Items</th>
                    <th>Total</th>
                    <th>Estado</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedOrders.map((order) => (
                    <tr key={order.id}>
                      <td>{order.id}</td>
                      <td className="mono">{order.userId}</td>
                      <td>{new Date(order.fecha).toLocaleString("es-AR")}</td>
                      <td>{order.items.reduce((sum, i) => sum + i.quantity, 0)}</td>
                      <td>{formatMoney(order.total)}</td>
                      <td>
                        <span className="status-badge">{order.estado}</span>
                      </td>
                      <td>
                        <select
                          value=""
                          onChange={(e) => {
                            if (e.target.value) void handleOrderStatusChange(order, e.target.value);
                          }}
                        >
                          <option value="">Cambiar estado...</option>
                          <option value="PENDIENTE">PENDIENTE</option>
                          <option value="COMPLETADA">COMPLETADA</option>
                          <option value="ENVIADA">ENVIADA</option>
                          <option value="CANCELADA">CANCELADA</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {orderTotalPages > 1 ? (
            <div className="pagination-row">
              <button className="secondary-btn" onClick={() => setOrderPage((p) => Math.max(1, p - 1))} disabled={orderCurrentPage <= 1}>
                ← Anterior
              </button>
              <span>
                Página {orderCurrentPage} de {orderTotalPages}
              </span>
              <button
                className="secondary-btn"
                onClick={() => setOrderPage((p) => Math.min(orderTotalPages, p + 1))}
                disabled={orderCurrentPage >= orderTotalPages}
              >
                Siguiente →
              </button>
            </div>
          ) : null}
        </section>
      ) : null}

      {tab === "products" ? (
        <section className="admin-section">
          <form className="admin-form-grid" onSubmit={handleProductSubmit}>
            <label>
              <span>Nombre</span>
              <input value={productForm.name} onChange={(e) => setProductForm({ ...productForm, name: e.target.value })} />
            </label>
            <label>
              <span>Marca</span>
              <input value={productForm.brand} onChange={(e) => setProductForm({ ...productForm, brand: e.target.value })} />
            </label>
            <label>
              <span>Precio</span>
              <input type="number" value={productForm.price} onChange={(e) => setProductForm({ ...productForm, price: e.target.value })} />
            </label>
            <label>
              <span>Precio anterior</span>
              <input type="number" value={productForm.oldPrice} onChange={(e) => setProductForm({ ...productForm, oldPrice: e.target.value })} />
            </label>
            <label>
              <span>Rating</span>
              <input type="number" step="0.1" value={productForm.rating} onChange={(e) => setProductForm({ ...productForm, rating: e.target.value })} />
            </label>
            <label>
              <span>Stock</span>
              <input type="number" value={productForm.stock} onChange={(e) => setProductForm({ ...productForm, stock: e.target.value })} />
            </label>
            <label>
              <span>Categoría</span>
              <select value={productForm.categoryId} onChange={(e) => setProductForm({ ...productForm, categoryId: e.target.value })}>
                <option value="">Seleccionar...</option>
                {categoryList.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Badge</span>
              <input value={productForm.badge} onChange={(e) => setProductForm({ ...productForm, badge: e.target.value })} />
            </label>
            <label>
              <span>Precio promocional</span>
              <input
                type="number"
                value={productForm.precioPromocional}
                onChange={(e) => setProductForm({ ...productForm, precioPromocional: e.target.value })}
              />
            </label>
            <label>
              <span>Tipo de promoción</span>
              <select
                value={productForm.tipoPromocion}
                onChange={(e) => handlePromoTypeChange(e.target.value)}
              >
                <option value="">Sin tipo</option>
                {PROMO_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
                {productForm.tipoPromocion && !PROMO_TYPES.includes(productForm.tipoPromocion) ? (
                  <option value={productForm.tipoPromocion}>{productForm.tipoPromocion}</option>
                ) : null}
              </select>
            </label>
            <label className="admin-form-wide">
              <span>URL de imagen</span>
              <input value={productForm.imageUrl} onChange={(e) => setProductForm({ ...productForm, imageUrl: e.target.value })} />
            </label>
            <label className="admin-form-wide">
              <span>Subir imagen</span>
              <input type="file" accept="image/png,image/jpeg,image/gif,image/webp" onChange={handleImageUpload} disabled={imageUploading} />
              {imageUploading ? <span className="upload-status">Subiendo...</span> : null}
              {productForm.imageUrl ? (
                <img src={productForm.imageUrl} alt="Vista previa" className="image-preview" />
              ) : null}
            </label>

            {productError ? <div className="error-box admin-form-wide">{productError}</div> : null}

            <div className="admin-form-actions admin-form-wide">
              <button className="primary-btn" type="submit">
                {editingProductId ? "Guardar cambios" : "Crear producto"}
              </button>
              {editingProductId ? (
                <button className="secondary-btn" type="button" onClick={resetProductForm}>
                  Cancelar edición
                </button>
              ) : null}
            </div>
          </form>

          {editingProductId ? (
            <section className="admin-subsection">
              <h3>Variantes de "{productForm.name}"</h3>
              <p className="admin-hint">
                Si este producto tiene talle y/o color, cada opción maneja su propio stock. El stock total del
                producto se calcula automáticamente sumando el de todas sus variantes.
              </p>

              <form className="admin-form-grid" onSubmit={handleVariantSubmit}>
                <label>
                  <span>Talle</span>
                  <input value={variantForm.talle} onChange={(e) => setVariantForm({ ...variantForm, talle: e.target.value })} placeholder="M, 42..." />
                </label>
                <label>
                  <span>Color</span>
                  <input value={variantForm.color} onChange={(e) => setVariantForm({ ...variantForm, color: e.target.value })} placeholder="Negro, Rojo..." />
                </label>
                <label>
                  <span>Stock</span>
                  <input type="number" value={variantForm.stock} onChange={(e) => setVariantForm({ ...variantForm, stock: e.target.value })} />
                </label>
                <label className="admin-form-wide">
                  <span>URL de imagen (opcional, si no usa la del producto)</span>
                  <input value={variantForm.imageUrl} onChange={(e) => setVariantForm({ ...variantForm, imageUrl: e.target.value })} />
                </label>
                <label className="admin-form-wide">
                  <span>Subir imagen de la variante</span>
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    onChange={handleVariantImageUpload}
                    disabled={variantImageUploading}
                  />
                  {variantImageUploading ? <span className="upload-status">Subiendo...</span> : null}
                  {variantForm.imageUrl ? <img src={variantForm.imageUrl} alt="Vista previa" className="image-preview" /> : null}
                </label>

                {variantError ? <div className="error-box admin-form-wide">{variantError}</div> : null}

                <div className="admin-form-actions admin-form-wide">
                  <button className="primary-btn" type="submit">
                    {editingVariantId ? "Guardar variante" : "Agregar variante"}
                  </button>
                  {editingVariantId ? (
                    <button className="secondary-btn" type="button" onClick={resetVariantForm}>
                      Cancelar
                    </button>
                  ) : null}
                </div>
              </form>

              {variantsLoading ? (
                <div className="empty-state">Cargando variantes...</div>
              ) : variantList.length === 0 ? (
                <div className="empty-state">Este producto todavía no tiene variantes.</div>
              ) : (
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>Foto</th>
                        <th>Talle</th>
                        <th>Color</th>
                        <th>Stock</th>
                        <th>Acciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {variantList.map((variant) => (
                        <tr key={variant.id}>
                          <td>
                            {variant.imageUrl ? (
                              <img src={variant.imageUrl} alt="" className="variant-thumb" />
                            ) : (
                              "—"
                            )}
                          </td>
                          <td>{variant.talle || "—"}</td>
                          <td>{variant.color || "—"}</td>
                          <td>{variant.stock}</td>
                          <td>
                            <div className="admin-row-actions">
                              <button className="secondary-btn" onClick={() => startEditVariant(variant)}>
                                Editar
                              </button>
                              <button className="secondary-btn danger" onClick={() => handleDeleteVariant(variant)}>
                                Eliminar
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          ) : null}

          {productsLoading ? (
            <div className="empty-state">Cargando productos...</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Marca</th>
                    <th>Precio</th>
                    <th>Promoción</th>
                    <th>Stock</th>
                    <th>Categoría</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((product) => (
                    <tr key={product.id}>
                      <td>{product.name}</td>
                      <td>{product.brand}</td>
                      <td>{formatMoney(product.price)}</td>
                      <td>
                        {product.precioPromocional != null
                          ? `${formatMoney(product.precioPromocional)} (${product.tipoPromocion || "s/tipo"})`
                          : "—"}
                      </td>
                      <td>{product.stock}</td>
                      <td>{product.categoryId}</td>
                      <td>
                        <div className="admin-row-actions">
                          <button className="secondary-btn" onClick={() => startEditProduct(product)}>
                            Editar
                          </button>
                          <button className="secondary-btn danger" onClick={() => handleDeleteProduct(product)}>
                            Eliminar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {tab === "categories" ? (
        <section className="admin-section">
          <form className="admin-form-grid" onSubmit={handleCategorySubmit}>
            <label>
              <span>Nombre</span>
              <input value={categoryForm.name} onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })} />
            </label>
            <label>
              <span>Color</span>
              <input
                type="color"
                value={categoryForm.color}
                onChange={(e) => setCategoryForm({ ...categoryForm, color: e.target.value })}
              />
            </label>

            {categoryError ? <div className="error-box admin-form-wide">{categoryError}</div> : null}

            <div className="admin-form-actions admin-form-wide">
              <button className="primary-btn" type="submit">
                {editingCategoryId ? "Guardar cambios" : "Crear categoría"}
              </button>
              {editingCategoryId ? (
                <button className="secondary-btn" type="button" onClick={resetCategoryForm}>
                  Cancelar edición
                </button>
              ) : null}
            </div>
          </form>

          {categoriesLoading ? (
            <div className="empty-state">Cargando categorías...</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Color</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {categoryList.map((category) => (
                    <tr key={category.id}>
                      <td>{category.name}</td>
                      <td>
                        <span className="category-swatch" style={{ background: category.color || "#e2e8f0" }} />
                        {category.color}
                      </td>
                      <td>
                        <div className="admin-row-actions">
                          <button className="secondary-btn" onClick={() => startEditCategory(category)}>
                            Editar
                          </button>
                          <button className="secondary-btn danger" onClick={() => handleDeleteCategory(category)}>
                            Eliminar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {tab === "returns" ? (
        <section className="admin-section">
          <h3>Pendientes de revisión</h3>
          {returnsLoading ? (
            <div className="empty-state">Cargando devoluciones...</div>
          ) : pendingReturns.length === 0 ? (
            <div className="empty-state">No hay devoluciones pendientes.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Usuario</th>
                    <th>Producto</th>
                    <th>Variante</th>
                    <th>Cantidad</th>
                    <th>Motivo</th>
                    <th>Solicitada</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingReturns.map((devolucion) => (
                    <tr key={devolucion.id}>
                      <td>{devolucion.id}</td>
                      <td className="mono">{devolucion.userId}</td>
                      <td>{devolucion.productId}</td>
                      <td>{devolucion.variant || "—"}</td>
                      <td>{devolucion.cantidad}</td>
                      <td>{devolucion.motivo}</td>
                      <td>{new Date(devolucion.requestedAt).toLocaleString("es-AR")}</td>
                      <td>
                        <div className="admin-row-actions">
                          <button className="secondary-btn" onClick={() => handleReturnDecision(devolucion, "APROBADA")}>
                            Aprobar
                          </button>
                          <button className="secondary-btn danger" onClick={() => handleReturnDecision(devolucion, "RECHAZADA")}>
                            Rechazar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h3>Aprobadas — pendientes de reembolso</h3>
          {returnsLoading ? (
            <div className="empty-state">Cargando devoluciones...</div>
          ) : approvedReturns.length === 0 ? (
            <div className="empty-state">No hay devoluciones esperando reembolso.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Usuario</th>
                    <th>Producto</th>
                    <th>Variante</th>
                    <th>Cantidad</th>
                    <th>Solicitada</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {approvedReturns.map((devolucion) => (
                    <tr key={devolucion.id}>
                      <td>{devolucion.id}</td>
                      <td className="mono">{devolucion.userId}</td>
                      <td>{devolucion.productId}</td>
                      <td>{devolucion.variant || "—"}</td>
                      <td>{devolucion.cantidad}</td>
                      <td>{new Date(devolucion.requestedAt).toLocaleString("es-AR")}</td>
                      <td>
                        <button className="secondary-btn" onClick={() => handleProcessRefund(devolucion)}>
                          Procesar reembolso
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {tab === "users" ? (
        <section className="admin-section">
          {usersLoading ? (
            <div className="empty-state">Cargando usuarios...</div>
          ) : users.length === 0 ? (
            <div className="empty-state">No hay usuarios registrados.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Email</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => {
                    const isSelf = user.id === currentUserId;
                    return (
                      <tr key={user.id}>
                        <td>{user.name}</td>
                        <td>{user.email}</td>
                        <td>{user.role}</td>
                        <td>
                          <span className="status-badge">{user.active === false ? "Deshabilitado" : "Activo"}</span>
                        </td>
                        <td>
                          <div className="admin-row-actions">
                            {isSelf ? (
                              <span className="review-login-hint">Tu cuenta</span>
                            ) : (
                              <>
                                <button
                                  className="secondary-btn"
                                  onClick={() => handleUserRoleChange(user, user.role === "ADMIN" ? "CUSTOMER" : "ADMIN")}
                                >
                                  {user.role === "ADMIN" ? "Quitar admin" : "Hacer admin"}
                                </button>
                                <button
                                  className="secondary-btn danger"
                                  onClick={() => handleUserStatusChange(user, user.active === false)}
                                >
                                  {user.active === false ? "Habilitar" : "Deshabilitar"}
                                </button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {tab === "announcements" ? (
        <AnnouncementSettings authToken={authToken} settings={siteSettings} onSaved={onSiteSettingsChange} />
      ) : null}

      {tab === "coupons" ? (
        <section className="admin-section">
          <form className="admin-form-grid" onSubmit={handleCouponSubmit}>
            <label>
              <span>Código</span>
              <input
                value={couponForm.code}
                onChange={(e) => setCouponForm({ ...couponForm, code: e.target.value.toUpperCase() })}
                placeholder="VERANO10"
              />
            </label>
            <label>
              <span>Tipo de descuento</span>
              <select
                value={couponForm.discountType}
                onChange={(e) => setCouponForm({ ...couponForm, discountType: e.target.value as "PERCENTAGE" | "FIXED" })}
              >
                <option value="PERCENTAGE">Porcentaje (%)</option>
                <option value="FIXED">Monto fijo ($)</option>
              </select>
            </label>
            <label>
              <span>Valor del descuento</span>
              <input
                type="number"
                value={couponForm.discountValue}
                onChange={(e) => setCouponForm({ ...couponForm, discountValue: e.target.value })}
              />
            </label>
            <label>
              <span>Compra mínima</span>
              <input
                type="number"
                value={couponForm.minPurchase}
                onChange={(e) => setCouponForm({ ...couponForm, minPurchase: e.target.value })}
              />
            </label>
            <label>
              <span>Usos máximos</span>
              <input
                type="number"
                min="1"
                placeholder="Ilimitado"
                value={couponForm.maxUses}
                onChange={(e) => setCouponForm({ ...couponForm, maxUses: e.target.value })}
              />
            </label>
            <label>
              <span>Vence (opcional)</span>
              <input
                type="date"
                value={couponForm.expiresAt}
                onChange={(e) => setCouponForm({ ...couponForm, expiresAt: e.target.value })}
              />
            </label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={couponForm.active}
                onChange={(e) => setCouponForm({ ...couponForm, active: e.target.checked })}
              />
              <span>Activo</span>
            </label>

            {couponError ? <div className="error-box admin-form-wide">{couponError}</div> : null}

            <div className="admin-form-actions admin-form-wide">
              <button className="primary-btn" type="submit">
                {editingCouponId ? "Guardar cambios" : "Crear cupón"}
              </button>
              {editingCouponId ? (
                <button className="secondary-btn" type="button" onClick={resetCouponForm}>
                  Cancelar edición
                </button>
              ) : null}
            </div>
          </form>

          {couponsLoading ? (
            <div className="empty-state">Cargando cupones...</div>
          ) : couponList.length === 0 ? (
            <div className="empty-state">No hay cupones creados todavía.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Código</th>
                    <th>Descuento</th>
                    <th>Compra mínima</th>
                    <th>Usos</th>
                    <th>Vence</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {couponList.map((coupon) => (
                    <tr key={coupon.id}>
                      <td className="mono">{coupon.code}</td>
                      <td>
                        {coupon.discountType === "PERCENTAGE"
                          ? `${coupon.discountValue}%`
                          : formatMoney(coupon.discountValue)}
                      </td>
                      <td>{coupon.minPurchase ? formatMoney(coupon.minPurchase) : "—"}</td>
                      <td>
                        {coupon.usesCount}
                        {coupon.maxUses != null ? ` / ${coupon.maxUses}` : ""}
                      </td>
                      <td>{coupon.expiresAt ? new Date(coupon.expiresAt).toLocaleDateString("es-AR") : "—"}</td>
                      <td>
                        <span className="status-badge">{coupon.active ? "Activo" : "Inactivo"}</span>
                      </td>
                      <td>
                        <div className="admin-row-actions">
                          <button className="secondary-btn" onClick={() => startEditCoupon(coupon)}>
                            Editar
                          </button>
                          <button className="secondary-btn danger" onClick={() => handleDeleteCoupon(coupon)}>
                            Eliminar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </div>
  );
}
