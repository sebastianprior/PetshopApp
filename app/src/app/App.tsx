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
import { WishlistScreen } from "./screens/WishlistScreen";
import {
  addToCart,
  addToWishlist,
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
  createVenta,
  decrementCartItem,
  fetchCart,
  fetchCategories,
  fetchCurrentUser,
  fetchProducts,
  fetchWishlist,
  getAuthToken,
  getCurrentUser,
  incrementCartItem,
  login,
  register,
  removeFromCart,
  removeFromWishlist,
  UNAUTHORIZED_EVENT,
} from "./api";
import type { CartItem, CheckoutResult, Category, Product, ProductVariant, ShippingInfo, User, View } from "./types";

function App() {
  const [view, setView] = useState<View>("home");
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

  const cartCount = useMemo(
    () => cartItems.reduce((total, item) => total + item.quantity, 0),
    [cartItems],
  );

  const activePromos = useMemo(
    () => products.filter((product) => product.precioPromocional != null),
    [products],
  );

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
    setSelectedProductId(null);
    setView(nextView);
  };

  const handleLogout = () => {
    localStorage.removeItem(AUTH_TOKEN_KEY);
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
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
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
      return <AdminDashboardScreen authToken={authToken} categories={categories} currentUserId={currentUser.id} />;
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
      <header className="topbar">
        <div className="topbar-inner">
          <span>🚚 Envíos a todo el país</span>
          <span>💳 6 cuotas sin interés</span>
          <span>🏷️ Ofertas semanales</span>
        </div>
      </header>

      {activePromos.length > 0 ? (
        <div className="promo-banner">
          <div className="promo-banner-inner">
            🔥 ¡Ofertas! {activePromos.map((p) => `${p.name} ${p.tipoPromocion || ""}`.trim()).join(" · ")}
          </div>
        </div>
      ) : null}

      <nav className="main-nav">
        <div className="nav-inner">
          <button className="brand" onClick={() => navigateTo("home")}>
            <span className="brand-mark">🐾</span>
            <span>Petshop</span>
          </button>

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
      </nav>

      <main className="page-container">{renderScreen()}</main>

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
