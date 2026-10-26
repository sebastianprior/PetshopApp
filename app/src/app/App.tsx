import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { DailyRoulette } from "./screens/DailyRoulette";
import { WishlistScreen } from "./screens/WishlistScreen";
import { Icon } from "./components/Icon";
import { ToastRegion, type ToastMessage } from "./components/Toast";
import {
  addToCart,
  addToWishlist,
  AUTH_REFRESH_KEY,
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
  createVenta,
  decrementCartItem,
  fetchCart,
  fetchCartPromos,
  fetchCategories,
  fetchCurrentUser,
  fetchProducts,
  fetchRoulette,
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
import { NO_CART_PROMOS, type CartPromos } from "./types";
import type { CartItem, CheckoutResult, Category, Product, ProductVariant, RouletteState, ShippingInfo, SiteSettings, User, View } from "./types";

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
  const [roulette, setRoulette] = useState<RouletteState | null>(null);
  const [rouletteOpen, setRouletteOpen] = useState(false);
  // imagen de cada producto visto alguna vez: el carrito la necesita aunque el catálogo esté filtrado
  const [productImages, setProductImages] = useState<Record<string, string>>({});
  const [categories, setCategories] = useState<Category[]>([]);
  // categorías que tienen al menos un producto (se calcula con el catálogo completo, sin filtros)
  const [categoriesWithProducts, setCategoriesWithProducts] = useState<Set<string> | null>(null);
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
  const [navSearch, setNavSearch] = useState("");
  const [cartPromos, setCartPromos] = useState<CartPromos>(NO_CART_PROMOS);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  // La tienda solo muestra categorías con productos; el panel de admin sigue viendo todas
  const shopCategories = useMemo(
    () => (categoriesWithProducts ? categories.filter((category) => categoriesWithProducts.has(category.id)) : categories),
    [categories, categoriesWithProducts],
  );

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
      if (category === "all" && !query) {
        setCategoriesWithProducts(new Set(data.map((product) => product.categoryId)));
      }
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

  useEffect(() => {
    if (currentUser?.role === "ADMIN") {
      setRoulette(null);
      return;
    }
    fetchRoulette(authToken)
      .then(setRoulette)
      .catch((error) => {
        console.error("No se pudo cargar la ruleta del día", error);
        setRoulette(null);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser?.id, currentUser?.role]);

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
    fetchCartPromos()
      .then(setCartPromos)
      .catch((error) => console.error("No se pudo cargar el envío gratis y el regalo", error));
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

  const dismissToast = useCallback(() => setToast(null), []);
  const showToast = (message: Omit<ToastMessage, "id">) => setToast({ id: Date.now(), ...message });

  const navigateTo = (nextView: View) => {
    setMenuOpen(false);
    setSelectedProductId(null);
    // Inicio, Ofertas y Favoritos trabajan con el catálogo completo: al salir del catálogo se limpian los filtros.
    if (nextView !== "products") {
      setSelectedCategory("all");
      setSearch("");
      setNavSearch("");
    }
    setView(nextView);
  };

  /** Abre el catálogo, opcionalmente ya filtrado por una categoría. */
  const showCatalog = (categoryId = "all") => {
    setMenuOpen(false);
    setSelectedProductId(null);
    setSelectedCategory(categoryId);
    setView("products");
  };

  const handleNavSearch = (event: React.FormEvent) => {
    event.preventDefault();
    setSearch(navSearch.trim());
    showCatalog(selectedCategory);
  };

  // Con el menú móvil abierto, Escape lo cierra
  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: KeyboardEvent) => event.key === "Escape" && setMenuOpen(false);
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [menuOpen]);

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

  const handleAddToCart = async (product: Product, variant?: ProductVariant, quantity = 1) => {
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
        quantity,
        price: product.price,
      };
      const updated = await addToCart(authToken, nextItem);
      setCartItems(updated);
      showToast({
        text: `${product.name} se agregó al carrito`,
        actionLabel: "Ver carrito",
        onAction: () => navigateTo("cart"),
      });
    } catch (error) {
      console.error("Error agregando al carrito", error);
      showToast({ text: error instanceof Error ? error.message : "No se pudo agregar el producto al carrito.", tone: "error" });
    }
  };

  const handleToggleWishlist = async (productId: string) => {
    if (currentUser?.role === "ADMIN") {
      return;
    }
    if (!authToken) {
      showToast({
        text: "Iniciá sesión para guardar productos en favoritos.",
        actionLabel: "Ingresar",
        onAction: () => navigateTo("login"),
      });
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
      showToast({ text: error instanceof Error ? error.message : "No se pudo completar la compra.", tone: "error" });
    }
  };

  const handleIncrementCartItem = async (item: CartItem) => {
    try {
      const updated = await incrementCartItem(authToken, item.productId, item.variantId);
      setCartItems(updated);
    } catch (error) {
      console.error("Error sumando cantidad", error);
      showToast({ text: error instanceof Error ? error.message : "No se pudo sumar la cantidad.", tone: "error" });
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
          categories={shopCategories}
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
          rouletteCategoryId={roulette?.categoryId ?? null}
          roulettePercent={roulette?.percent}
        />
      );
    }

    if (view === "cart") {
      return (
        <CartScreen
          items={cartItems}
          authToken={authToken}
          productImages={productImages}
          promos={cartPromos}
          onRemove={handleRemoveFromCart}
          onCheckout={handleCheckout}
          onIncrement={handleIncrementCartItem}
          onDecrement={handleDecrementCartItem}
          onContinueShopping={() => showCatalog()}
        />
      );
    }

    if (view === "confirmation" && lastCheckout) {
      return (
        <OrderConfirmationScreen
          result={lastCheckout}
          isLoggedIn={!!currentUser}
          onNavigate={(next) => (next === "products" ? showCatalog() : navigateTo(next))}
        />
      );
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
          cartPromos={cartPromos}
          onCartPromosChange={setCartPromos}
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
        categories={shopCategories}
        products={products}
        currentUser={currentUser}
        onNavigate={(next) => (next === "products" ? showCatalog() : navigateTo(next))}
        onSelectCategory={showCatalog}
        onAddToCart={handleAddToCart}
        onOpenProduct={openProduct}
        wishlist={wishlist}
        onToggleWishlist={handleToggleWishlist}
        roulette={roulette}
        onOpenRoulette={() => setRouletteOpen(true)}
      />
    );
  };

  const isAdmin = currentUser?.role === "ADMIN";
  const themeOptions = [
    { id: "light", label: "Tema claro", icon: "sun" },
    { id: "soft", label: "Tema medio", icon: "contrast" },
    { id: "dark", label: "Tema oscuro", icon: "moon" },
  ] as const;

  const navLink = (target: View, label: string) => (
    <button
      className={view === target && !selectedProductId ? "nav-link active" : "nav-link"}
      aria-current={view === target && !selectedProductId ? "page" : undefined}
      onClick={() => (target === "products" ? showCatalog() : navigateTo(target))}
    >
      {label}
    </button>
  );

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

      <nav className="main-nav" aria-label="Principal">
        <div className="nav-inner">
          <button className="brand" onClick={() => navigateTo("home")} aria-label="Petshop, ir al inicio">
            <span className="brand-mark">
              <Icon name="paw" size={22} />
            </span>
            <span className="brand-name">Petshop</span>
          </button>

          <form className="nav-search" role="search" onSubmit={handleNavSearch}>
            <Icon name="search" size={18} />
            <input
              type="search"
              value={navSearch}
              onChange={(e) => setNavSearch(e.target.value)}
              placeholder="Buscá productos o marcas..."
              aria-label="Buscar productos"
            />
          </form>

          <div className="nav-quick">
            {roulette ? (
              <button className="nav-roulette-icon" onClick={() => setRouletteOpen(true)} aria-label="Descuento del día">
                <Icon name="wheel" size={22} />
                {!roulette.spun ? <span className="nav-roulette-dot" /> : null}
              </button>
            ) : null}
            {currentUser && !isAdmin ? (
              <button className="nav-icon-btn" onClick={() => navigateTo("wishlist")} aria-label={`Favoritos (${wishlist.length})`}>
                <Icon name="heart" size={22} />
                {wishlist.length > 0 ? <span className="nav-cart-count">{wishlist.length}</span> : null}
              </button>
            ) : null}
            {!isAdmin ? (
              <button className="nav-cart" onClick={() => navigateTo("cart")} aria-label={`Carrito (${cartCount})`}>
                <Icon name="cart" size={22} />
                {cartCount > 0 ? <span className="nav-cart-count">{cartCount}</span> : null}
              </button>
            ) : null}
            <button
              className="nav-toggle"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label={menuOpen ? "Cerrar menú" : "Abrir menú"}
              aria-expanded={menuOpen}
            >
              <Icon name={menuOpen ? "close" : "menu"} size={24} />
            </button>
          </div>

          <div className={menuOpen ? "nav-menu open" : "nav-menu"}>
            <div className="nav-links">
              {navLink("home", "Inicio")}
              {navLink("products", "Productos")}
              {navLink("offers", "Ofertas")}
              {!isAdmin ? <span className="nav-only-mobile">{navLink("cart", `Carrito (${cartCount})`)}</span> : null}
              {currentUser && !isAdmin ? (
                <span className="nav-only-mobile">{navLink("wishlist", `Favoritos${wishlist.length > 0 ? ` (${wishlist.length})` : ""}`)}</span>
              ) : null}
              {currentUser ? navLink("orders", "Mis pedidos") : null}
              {isAdmin ? navLink("admin", "Admin") : null}
            </div>

            <div className="nav-actions">
              {roulette ? (
                <button className={roulette.spun ? "roulette-pill won" : "roulette-pill"} onClick={() => setRouletteOpen(true)}>
                  <Icon name="wheel" size={18} />
                  {roulette.spun ? `${roulette.percent}% OFF · ${roulette.categoryName}` : "Descuento del día"}
                  {!roulette.spun ? <span className="nav-roulette-dot" /> : null}
                </button>
              ) : null}
              <div className="theme-switcher" role="group" aria-label="selector de tema">
                {themeOptions.map((option) => (
                  <button
                    key={option.id}
                    className={theme === option.id ? "theme-option active" : "theme-option"}
                    onClick={() => setTheme(option.id)}
                    aria-label={option.label}
                    aria-pressed={theme === option.id}
                    title={option.label}
                  >
                    <Icon name={option.icon} size={16} />
                  </button>
                ))}
              </div>

              {currentUser ? (
                <>
                  <button className="user-pill" onClick={() => navigateTo("account")}>
                    <Icon name="user" size={16} /> {currentUser.name}
                  </button>
                  <button className="secondary-btn" onClick={handleLogout}>
                    <Icon name="logout" size={16} /> Salir
                  </button>
                </>
              ) : (
                <button className="primary-btn" onClick={() => navigateTo("login")}>Iniciar sesión</button>
              )}
            </div>
          </div>
        </div>
      </nav>

      <main className="page-container">{renderScreen()}</main>

      {rouletteOpen && roulette ? (
        <DailyRoulette
          state={roulette}
          token={authToken}
          onSpun={setRoulette}
          onClose={() => setRouletteOpen(false)}
          onGoToCategory={(categoryId) => {
            setRouletteOpen(false);
            showCatalog(categoryId);
          }}
        />
      ) : null}

      {!isAdmin ? <SalchiBot onOpenProduct={openProduct} onAddToCart={handleAddToCart} /> : null}

      <ToastRegion toast={toast} onDismiss={dismissToast} />

      <footer className="app-footer">
        <div className="app-footer-inner">
          <div className="footer-brand">
            <span className="brand-mark">
              <Icon name="paw" size={22} />
            </span>
            <div>
              <strong>Petshop</strong>
              <p>Todo para perros y gatos, con envíos a todo el país.</p>
            </div>
          </div>
          <div className="footer-col">
            <h4>Tienda</h4>
            <button onClick={() => showCatalog()}>Productos</button>
            <button onClick={() => navigateTo("offers")}>Ofertas</button>
            {shopCategories.some((category) => category.id === "perros") ? <button onClick={() => showCatalog("perros")}>Perros</button> : null}
            {shopCategories.some((category) => category.id === "gatos") ? <button onClick={() => showCatalog("gatos")}>Gatos</button> : null}
          </div>
          <div className="footer-col">
            <h4>Ayuda</h4>
            <button onClick={() => navigateTo("contact")}>Contacto</button>
            <button onClick={() => navigateTo("terms")}>Términos y condiciones</button>
            <button onClick={() => navigateTo("privacy")}>Privacidad</button>
          </div>
        </div>
        <div className="app-footer-bottom">
          <span>© {new Date().getFullYear()} PetshopApp — tienda de demostración</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
