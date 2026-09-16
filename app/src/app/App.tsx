import { useEffect, useMemo, useState } from "react";
import { HomeScreen } from "./screens/HomeScreen";
import { ProductsScreen } from "./screens/ProductsScreen";
import { CartScreen } from "./screens/CartScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { ProductDetailScreen } from "./screens/ProductDetailScreen";
import { OffersScreen } from "./screens/OffersScreen";
import { AdminDashboardScreen } from "./screens/AdminDashboardScreen";
import {
  addToCart,
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
  checkoutCart,
  fetchCart,
  fetchCategories,
  fetchCurrentUser,
  fetchProducts,
  getAuthToken,
  getCurrentUser,
  login,
  register,
  removeFromCart,
} from "./api";
import type { CartItem, Category, Product, User, View } from "./types";

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
  const [selectedProductId, setSelectedProductId] = useState<string | null>(null);
  const [theme, setTheme] = useState<"light" | "soft" | "dark">("soft");

  const cartCount = useMemo(
    () => cartItems.reduce((total, item) => total + item.quantity, 0),
    [cartItems],
  );

  const loadCategories = async () => {
    try {
      const data = await fetchCategories();
      setCategories(data);
    } catch (error) {
      console.error("No se pudieron cargar las categorías", error);
    }
  };

  const loadProducts = async (category = selectedCategory, order = sort) => {
    setProductLoading(true);
    try {
      const data = await fetchProducts(category, order);
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

  useEffect(() => {
    const token = getAuthToken();
    const user = getCurrentUser();
    if (token) {
      setAuthToken(token);
      setCurrentUser(user);
      void loadCart(token);
    }
    void loadCategories();
    void loadProducts();
  }, []);

  useEffect(() => {
    void loadProducts(selectedCategory, sort);
  }, [selectedCategory, sort]);

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
    setView("home");
  };

  const handleAddToCart = async (product: Product) => {
    try {
      const nextItem: CartItem = {
        productId: product.id,
        name: product.name,
        variant: product.categoryId || product.brand,
        quantity: 1,
        price: product.price,
      };
      const updated = await addToCart(authToken, nextItem);
      setCartItems(updated);
      setView("cart");
    } catch (error) {
      console.error("Error agregando al carrito", error);
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

  const handleCheckout = async () => {
    try {
      await checkoutCart(authToken);
      setCartItems([]);
      alert("Compra simulada completada.");
    } catch (error) {
      console.error("Error al finalizar la compra", error);
      alert("No se pudo completar la compra.");
    }
  };

  const handleAddQty = async (product: Product) => {
    await handleAddToCart(product);
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
      return <ProductDetailScreen product={selectedProduct} onBack={handleProductDetailBack} onAddToCart={handleAddToCart} />;
    }

    if (view === "products") {
      return (
        <ProductsScreen
          products={products}
          loading={productLoading}
          categoryFilter={selectedCategory}
          sort={sort}
          onCategoryChange={(categoryId) => {
            setSelectedCategory(categoryId);
            void loadProducts(categoryId, sort);
          }}
          onSortChange={(nextSort) => {
            setSort(nextSort);
            void loadProducts(selectedCategory, nextSort);
          }}
          onAddToCart={handleAddToCart}
          onOpenProduct={openProduct}
        />
      );
    }

    if (view === "cart") {
      return <CartScreen items={cartItems} onRemove={handleRemoveFromCart} onCheckout={handleCheckout} onAddQty={handleAddQty} />;
    }

    if (view === "offers") {
      return <OffersScreen products={products} onAddToCart={handleAddToCart} onOpenProduct={openProduct} />;
    }

    if (view === "admin" && currentUser?.role === "ADMIN" && authToken) {
      return <AdminDashboardScreen authToken={authToken} categories={categories} />;
    }

    return (
      <HomeScreen
        categories={categories}
        products={products}
        currentUser={currentUser}
        onNavigate={navigateTo}
        onAddToCart={handleAddToCart}
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
            <button onClick={() => navigateTo("cart")}>Carrito ({cartCount})</button>
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
                <span className="user-pill">{currentUser.name}</span>
                <button className="secondary-btn" onClick={handleLogout}>Salir</button>
              </>
            ) : (
              <button className="primary-btn" onClick={() => navigateTo("login")}>Iniciar sesión</button>
            )}
          </div>
        </div>
      </nav>

      <main className="page-container">{renderScreen()}</main>
    </div>
  );
}

export default App;
