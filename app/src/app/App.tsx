import { useEffect, useMemo, useRef, useState } from "react";
import { HomeScreen } from "./screens/HomeScreen";
import { ProductsScreen } from "./screens/ProductsScreen";
import { CartScreen } from "./screens/CartScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { ProductDetailScreen } from "./screens/ProductDetailScreen";
import { OffersScreen } from "./screens/OffersScreen";
import { AdminDashboardScreen } from "./screens/AdminDashboardScreen";
import { MyOrdersScreen } from "./screens/MyOrdersScreen";
import { MyAccountScreen } from "./screens/MyAccountScreen";
import { OrderConfirmationScreen } from "./screens/OrderConfirmationScreen";
import { LegalScreen } from "./screens/LegalScreen";
import { SplashScreen } from "./screens/SplashScreen";
import { SalchiBot } from "./screens/SalchiBot";
import { WishlistScreen } from "./screens/WishlistScreen";
import {
  addToCart,
  addToWishlist,
  AUTH_REFRESH_KEY,
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
  createVenta,
  decrementCartItem,
  fetchCart,
  fetchCategories,
  fetchCurrentUser,
  fetchProducts,
  fetchSiteSettings,
  fetchWishlist,
  getAuthToken,
  getCurrentUser,
  incrementCartItem,
  login,
  register,
  removeFromCart,
  removeFromWishlist,
  logoutSession,
  TOKEN_REFRESHED_EVENT,
  UNAUTHORIZED_EVENT,
} from "./api";
import type { CartItem, CheckoutResult, Category, Product, ProductVariant, ShippingInfo, SiteSettings, User, View } from "./types";

// Valores que se muestran hasta que llega la configuración del servidor (los mismos que trae por defecto).
const DEFAULT_SITE_SETTINGS: SiteSettings = {
  topbarItems: ["🚚 Envíos a todo el país", "💳 6 cuotas sin interés", "🏷️ Ofertas semanales"],
  announcementMode: "AUTO",
  announcementText: "",
};

function App() {
  const [view, setView] = useState<View>("home");
  const [menuOpen, setMenuOpen] = useState(false);
  const [showSplash, setShowSplash] = useState(true);
  // imagen de cada producto visto alguna vez: el carrito la necesita aunque el catálogo esté filtrado
  const [productImages, setProductImages] = useState<Record<string, string>>({});
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [authToken, setAuthToken] = useState<string | null>(null);
  const [productLoading, setProductLoading] = useState(false);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [authError, setAuthError] = useState<string | null>(null);
  const [authLoading, setAuthLoading] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>("all");
  const [sort, setSort] = useState("");
  const [search, setSearch] = useState("");
  const [selectedProductId, setSelectedProductId] = useState<string | null>(null);
  const [theme, setTheme] = useState<"light" | "soft" | "dark">("soft");
  const [lastCheckout, setLastCheckout] = useState<CheckoutResult | null>(null);
  const [wishlist, setWishlist] = useState<string[]>([]);
  const [siteSettings, setSiteSettings] = useState<SiteSettings>(DEFAULT_SITE_SETTINGS);

  const cartCount = useMemo(
    () => cartItems.reduce((total, item) => total + item.quantity, 0),
    [cartItems],
  );

  const activePromos = useMemo(
    () => products.filter((product) => product.precioPromocional != null),
    [products],
  );

  const announcementText =
    siteSettings.announcementMode === "CUSTOM"
      ? siteSettings.announcementText
      : siteSettings.announcementMode === "AUTO" && activePromos.length > 0
        ? `🔥 ¡Ofertas! ${activePromos.map((p) => `${p.name} ${p.tipoPromocion || ""}`.trim()).join(" · ")}`
        : "";

  const loadCategories = async () => {
    try {
      const data = await fetchCategories();
      setCategories(data);
    } catch (error) {
      console.error("No se pudieron cargar las categorías", error);
    }
  };

  const loadProducts = async (category = selectedCategory, order = sort, query = search) => {
    setProductLoading(true);
    try {
      const data = await fetchProducts(category, order, query);
      setProducts(data);
      setProductImages((known) => ({
        ...known,
        ...Object.fromEntries(data.filter((product) => product.imageUrl).map((product) => [product.id, product.imageUrl as string])),
      }));
    } catch (error) {
      console.error("No se pudieron cargar los productos", error);
    } finally {
      setProductLoading(false);
    }
  };

  const loadCart = async (token = authToken) => {
    try {
      const data = await fetchCart(token);
      setCartItems(data);
    } catch (error) {
      console.error("No se pudo cargar el carrito", error);
    }
  };

  const loadWishlist = async (token: string | null) => {
    if (!token) {
      setWishlist([]);
      return;
    }
    try {
      setWishlist(await fetchWishlist(token));
    } catch (error) {
      console.error("No se pudo cargar la lista de favoritos", error);
    }
  };

  useEffect(() => {
    const token = getAuthToken();
    const user = getCurrentUser();
    if (token) {
      setAuthToken(token);
      setCurrentUser(user);
      void loadCart(token);
      if (user?.role !== "ADMIN") {
        void loadWishlist(token);
      }
    }
    void loadCategories();
    void loadProducts();
    fetchSiteSettings()
      .then(setSiteSettings)
      .catch((error) => console.error("No se pudo cargar la configuración del sitio", error));
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      void loadProducts(selectedCategory, sort, search);
    }, 300);
    return () => clearTimeout(timer);
  }, [selectedCategory, sort, search]);

  useEffect(() => {
    if (authToken) {
      void loadCart(authToken);
    }
  }, [authToken]);

  const selectedProduct = useMemo(
    () => products.find((product) => product.id === selectedProductId) ?? null,
    [products, selectedProductId],
  );

  const handleLogin = async ({ email, password, name }: { email: string; password: string; name?: string }) => {
    if (!email || !password) {
      setAuthError("Completa email y contraseña.");
      return;
    }

    setAuthLoading(true);
    setAuthError(null);

    try {
      const response =
        authMode === "login"
          ? await login(email, password)
          : await register(email, password, name || email.split("@")[0]);

      localStorage.setItem(AUTH_TOKEN_KEY, response.token);
      localStorage.setItem(AUTH_REFRESH_KEY, response.refreshToken);
      localStorage.setItem(AUTH_USER_KEY, JSON.stringify(response.user));
      setAuthToken(response.token);
      setCurrentUser(response.user);
      setView("home");
      await loadCart(response.token);
      if (response.user.role !== "ADMIN") {
        await loadWishlist(response.token);
      }
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Error de autenticación");
    } finally {
      setAuthLoading(false);
    }
  };

  const navigateTo = (nextView: View) => {
    setMenuOpen(false);
    setSelectedProductId(null);
    setView(nextView);
  };

  const handleLogout = () => {
    void logoutSession();
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_REFRESH_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
    setAuthToken(null);
    setCurrentUser(null);
    setSelectedProductId(null);
    setWishlist([]);
    setView("home");
  };

  useEffect(() => {
    const handleUnauthorized = () => {
      if (!getAuthToken()) return;
      handleLogout();
      setAuthError("Tu sesión expiró. Iniciá sesión de nuevo.");
      setView("login");
    };
    const handleTokenRefreshed = (event: Event) => {
      const session = (event as CustomEvent<{ token: string; user: User }>).detail;
      setAuthToken(session.token);
      setCurrentUser(session.user);
    };
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    window.addEventListener(TOKEN_REFRESHED_EVENT, handleTokenRefreshed);
    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
      window.removeEventListener(TOKEN_REFRESHED_EVENT, handleTokenRefreshed);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleAddToCart = async (product: Product, variant?: ProductVariant) => {
    if (currentUser?.role === "ADMIN") {
      return;
    }
    if (product.hasVariants && !variant) {
      openProduct(product.id);
      return;
    }
    try {
      const variantLabel = variant
        ? [variant.talle ? `Talle ${variant.talle}` : null, variant.color].filter(Boolean).join(" / ")
        : product.categoryId || product.brand;
      const nextItem: CartItem = {
        productId: product.id,
        name: product.name,
        variant: variantLabel,
        variantId: variant?.id ?? null,
        quantity: 1,
        price: product.price,
      };
      const updated = await addToCart(authToken, nextItem);
      setCartItems(updated);
      setView("cart");
    } catch (error) {
      console.error("Error agregando al carrito", error);
      alert(error instanceof Error ? error.message : "No se pudo agregar el producto al carrito.");
    }
  };

  const handleToggleWishlist = async (productId: string) => {
    if (currentUser?.role === "ADMIN") {
      return;
    }
    if (!authToken) {
      alert("Iniciá sesión para guardar productos en favoritos.");
      return;
    }
    try {
      const updated = wishlist.includes(productId)
        ? await removeFromWishlist(authToken, productId)
        : await addToWishlist(authToken, productId);
      setWishlist(updated);
    } catch (error) {
      console.error("Error actualizando favoritos", error);
    }
  };

  const handleRemoveFromCart = async (item: CartItem) => {
    try {
      const updated = await removeFromCart(authToken, item);
      setCartItems(updated);
    } catch (error) {
      console.error("Error removiendo del carrito", error);
    }
  };

  const idempotencyKeyRef = useRef<string | null>(null);

  const handleCheckout = async (shipping: Partial<ShippingInfo>, couponCode?: string | null, medioPago?: string) => {
    try {
      idempotencyKeyRef.current ??= crypto.randomUUID();
      const result = await createVenta(authToken, shipping, couponCode, medioPago ?? "tok_aprobado", idempotencyKeyRef.current);
      idempotencyKeyRef.current = null;
      setCartItems([]);
      setLastCheckout(result);
      setView("confirmation");
    } catch (error) {
      // Solo se reutiliza la Idempotency-Key si no hubo respuesta HTTP (corte de red): así el reintento no duplica la venta.
      if (!(error instanceof TypeError)) {
        idempotencyKeyRef.current = null;
      }
      console.error("Error al finalizar la compra", error);
      alert(error instanceof Error ? error.message : "No se pudo completar la compra.");
    }
  };

  const handleIncrementCartItem = async (item: CartItem) => {
    try {
      const updated = await incrementCartItem(authToken, item.productId, item.variantId);
      setCartItems(updated);
    } catch (error) {
      console.error("Error sumando cantidad", error);
      alert(error instanceof Error ? error.message : "No se pudo sumar la cantidad.");
    }
  };

  const handleDecrementCartItem = async (item: CartItem) => {
    try {
      const updated = await decrementCartItem(authToken, item.productId, item.variantId);
      setCartItems(updated);
    } catch (error) {
      console.error("Error restando cantidad", error);
    }
  };

  const openProduct = (id: string) => {
    setSelectedProductId(id);
    setView("products");
  };

  const handleProductDetailBack = () => {
    setSelectedProductId(null);
    setView("products");
  };

  const renderScreen = () => {
    if (view === "login") {
      return (
        <LoginScreen
          mode={authMode}
          error={authError}
          loading={authLoading}
          onSubmit={handleLogin}
          onToggleMode={() => setAuthMode((prev) => (prev === "login" ? "register" : "login"))}
        />
      );
    }

    if (view === "products" && selectedProductId && selectedProduct) {
      return (
        <ProductDetailScreen
          product={selectedProduct}
          authToken={authToken}
          isAdmin={currentUser?.role === "ADMIN"}
          wishlist={wishlist}
          onToggleWishlist={handleToggleWishlist}
          onBack={handleProductDetailBack}
          onAddToCart={handleAddToCart}
        />
      );
    }

    if (view === "products") {
      return (
        <ProductsScreen
          products={products}
          loading={productLoading}
          categoryFilter={selectedCategory}
          sort={sort}
          search={search}
          onCategoryChange={setSelectedCategory}
          onSortChange={setSort}
          onSearchChange={setSearch}
          onAddToCart={handleAddToCart}
          onOpenProduct={openProduct}
          isAdmin={currentUser?.role === "ADMIN"}
          wishlist={wishlist}
          onToggleWishlist={handleToggleWishlist}
        />
      );
    }

    if (view === "cart") {
      return (
        <CartScreen
          items={cartItems}
          productImages={productImages}
          onRemove={handleRemoveFromCart}
          onCheckout={handleCheckout}
          onIncrement={handleIncrementCartItem}
          onDecrement={handleDecrementCartItem}
        />
      );
    }

    if (view === "confirmation" && lastCheckout) {
      return <OrderConfirmationScreen result={lastCheckout} isLoggedIn={!!currentUser} onNavigate={navigateTo} />;
    }

    if (view === "terms" || view === "privacy" || view === "contact") {
      return <LegalScreen section={view} />;
    }

    if (view === "offers") {
      return (
        <OffersScreen
          products={products}
          onAddToCart={handleAddToCart}
          onOpenProduct={openProduct}
          isAdmin={currentUser?.role === "ADMIN"}
          wishlist={wishlist}
          onToggleWishlist={handleToggleWishlist}
        />
      );
    }

    if (view === "admin" && currentUser?.role === "ADMIN" && authToken) {
      return (
        <AdminDashboardScreen
          authToken={authToken}
          categories={categories}
          currentUserId={currentUser.id}
          siteSettings={siteSettings}
          onSiteSettingsChange={setSiteSettings}
        />
      );
    }

    if (view === "orders" && currentUser && authToken) {
      return <MyOrdersScreen authToken={authToken} />;
    }

    if (view === "wishlist" && currentUser && currentUser.role !== "ADMIN") {
      return (
        <WishlistScreen
          products={products}
          wishlist={wishlist}
          onAddToCart={handleAddToCart}
          onOpenProduct={openProduct}
          onToggleWishlist={handleToggleWishlist}
        />
      );
    }

    if (view === "account" && currentUser && authToken) {
      return (
        <MyAccountScreen
          authToken={authToken}
          currentUser={currentUser}
          onProfileUpdated={(updated) => {
            setCurrentUser(updated);
            localStorage.setItem(AUTH_USER_KEY, JSON.stringify(updated));
          }}
        />
      );
    }

    return (
      <HomeScreen
        categories={categories}
        products={products}
        currentUser={currentUser}
        onNavigate={navigateTo}
        onAddToCart={handleAddToCart}
        onOpenProduct={openProduct}
        wishlist={wishlist}
        onToggleWishlist={handleToggleWishlist}
      />
    );
  };

  return (
    <div className={`app-shell theme-${theme}`}>
      {showSplash ? <SplashScreen onDone={() => setShowSplash(false)} /> : null}

      <header className="topbar">
        {siteSettings.topbarItems.length > 0 ? (
          <div className="topbar-inner">
            {siteSettings.topbarItems.map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        ) : null}
      </header>

      {announcementText ? (
        <div className="promo-banner">
          <div className="promo-banner-inner">{announcementText}</div>
        </div>
      ) : null}

      <nav className="main-nav">
        <div className="nav-inner">
          <button className="brand" onClick={() => navigateTo("home")}>
            <span className="brand-mark">🐾</span>
            <span>Petshop</span>
          </button>

          <div className="nav-quick">
            {currentUser?.role !== "ADMIN" ? (
              <button className="nav-cart" onClick={() => navigateTo("cart")} aria-label={`Carrito (${cartCount})`}>
                🛒
                {cartCount > 0 ? <span className="nav-cart-count">{cartCount}</span> : null}
              </button>
            ) : null}
            <button
              className="nav-toggle"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label={menuOpen ? "Cerrar menú" : "Abrir menú"}
              aria-expanded={menuOpen}
            >
              {menuOpen ? "✕" : "☰"}
            </button>
          </div>

          <div className={menuOpen ? "nav-menu open" : "nav-menu"}>
            <div className="nav-links">
              <button onClick={() => navigateTo("home")}>Inicio</button>
              <button onClick={() => navigateTo("products")}>Productos</button>
              <button onClick={() => navigateTo("offers")}>Ofertas</button>
              {currentUser?.role !== "ADMIN" ? (
                <button onClick={() => navigateTo("cart")}>Carrito ({cartCount})</button>
              ) : null}
              {currentUser && currentUser.role !== "ADMIN" ? (
                <button onClick={() => navigateTo("wishlist")}>Favoritos {wishlist.length > 0 ? `(${wishlist.length})` : ""}</button>
              ) : null}
              {currentUser ? (
                <button onClick={() => navigateTo("orders")}>Mis pedidos</button>
              ) : null}
              {currentUser?.role === "ADMIN" ? (
                <button onClick={() => navigateTo("admin")}>Admin</button>
              ) : null}
            </div>

            <div className="nav-actions">
              <div className="theme-switcher" aria-label="selector de tema">
                <button
                  className={theme === "light" ? "theme-option active" : "theme-option"}
                  onClick={() => setTheme("light")}
                  aria-label="Tema claro"
                  title="Tema claro"
                >
                  ☀️
                </button>
                <button
                  className={theme === "soft" ? "theme-option active" : "theme-option"}
                  onClick={() => setTheme("soft")}
                  aria-label="Tema medio"
                  title="Tema medio"
                >
                  ◐
                </button>
                <button
                  className={theme === "dark" ? "theme-option active" : "theme-option"}
                  onClick={() => setTheme("dark")}
                  aria-label="Tema oscuro"
                  title="Tema oscuro"
                >
                  🌙
                </button>
              </div>

              {currentUser ? (
                <>
                  <button className="user-pill" onClick={() => navigateTo("account")}>{currentUser.name}</button>
                  <button className="secondary-btn" onClick={handleLogout}>Salir</button>
                </>
              ) : (
                <button className="primary-btn" onClick={() => navigateTo("login")}>Iniciar sesión</button>
              )}
            </div>
          </div>
        </div>
      </nav>

      <main className="page-container">{renderScreen()}</main>

      {currentUser?.role !== "ADMIN" ? <SalchiBot onOpenProduct={openProduct} onAddToCart={handleAddToCart} /> : null}

      <footer className="app-footer">
        <div className="app-footer-inner">
          <span>© {new Date().getFullYear()} PetshopApp — tienda de demostración</span>
          <div className="app-footer-links">
            <button onClick={() => navigateTo("terms")}>Términos y condiciones</button>
            <button onClick={() => navigateTo("privacy")}>Privacidad</button>
            <button onClick={() => navigateTo("contact")}>Contacto</button>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
