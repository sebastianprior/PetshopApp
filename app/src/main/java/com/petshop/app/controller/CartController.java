package com.petshop.app.controller;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Coupon;
import com.petshop.app.model.Order;
import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.repository.CartItemRepository;
import com.petshop.app.repository.CouponRepository;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.service.InMemoryStore;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.Pricing;
import com.petshop.app.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final String GUEST_PREFIX = "guest:";
    private static final String GUEST_FALLBACK = "guest:unknown";
    private static final double SHIPPING_COST = 1500.0;

    private final InMemoryStore store;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    public CartController(InMemoryStore store, ProductRepository productRepository, CartItemRepository cartItemRepository,
                           OrderRepository orderRepository, JwtUtil jwtUtil, NotificationService notificationService,
                           ProductVariantRepository variantRepository, CouponRepository couponRepository) {
        this.store = store;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.jwtUtil = jwtUtil;
        this.notificationService = notificationService;
        this.variantRepository = variantRepository;
        this.couponRepository = couponRepository;
    }

    private int availableStock(Product product, Long variantId) {
        if (variantId == null) {
            return product.stock;
        }
        ProductVariant variant = variantRepository.findById(variantId).orElse(null);
        return variant != null ? variant.stock : 0;
    }

    private String variantLabel(ProductVariant variant) {
        List<String> parts = new ArrayList<>();
        if (variant.talle != null && !variant.talle.isBlank()) {
            parts.add("Talle " + variant.talle);
        }
        if (variant.color != null && !variant.color.isBlank()) {
            parts.add(variant.color);
        }
        return String.join(" / ", parts);
    }

    private String resolveToken(String token, String guestId) {
        if (token != null && !token.isBlank() && jwtUtil.isTokenValid(token)) {
            return jwtUtil.extractUserId(token);
        }
        if (guestId != null && !guestId.isBlank()) {
            return GUEST_PREFIX + guestId;
        }
        return GUEST_FALLBACK;
    }

    private boolean isGuest(String userToken) {
        return userToken.startsWith(GUEST_PREFIX);
    }

    private boolean isAdminToken(String token) {
        return token != null && !token.isBlank() && jwtUtil.isTokenValid(token) && "ADMIN".equals(jwtUtil.extractRole(token));
    }

    @GetMapping
    public ResponseEntity<?> getCart(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                      @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String userToken = resolveToken(token, guestId);
        List<CartItem> items = isGuest(userToken)
                ? store.carts.getOrDefault(userToken, List.of())
                : cartItemRepository.findByUserId(userToken);
        return ResponseEntity.ok(withCurrentPrices(items, !isGuest(userToken)));
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                  @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                  @RequestBody CartItem item) {
        if (isAdminToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Los administradores no pueden usar el carrito"));
        }

        String userToken = resolveToken(token, guestId);
        if (item == null || item.productId == null || item.productId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto inválido"));
        }

        Product product = productRepository.findById(item.productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        int stockAvailable = product.stock;
        if (item.variantId != null) {
            ProductVariant variant = variantRepository.findById(item.variantId).orElse(null);
            if (variant == null || !variant.productId.equals(item.productId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Variante no encontrada"));
            }
            stockAvailable = variant.stock;
            if (item.variant == null || item.variant.isBlank()) {
                item.variant = variantLabel(variant);
            }
        }

        item.name = item.name == null || item.name.isBlank() ? product.name : item.name;
        item.variant = item.variant == null || item.variant.isBlank() ? product.categoryId : item.variant;
        item.price = Pricing.effectivePrice(product);
        item.quantity = Math.max(1, item.quantity);

        final int finalStockAvailable = stockAvailable;

        if (isGuest(userToken)) {
            List<CartItem> cart = store.carts.computeIfAbsent(userToken, k -> new ArrayList<>());
            for (CartItem existing : cart) {
                if (existing.productId.equals(item.productId) && Objects.equals(existing.variantId, item.variantId)) {
                    if (existing.quantity + item.quantity > finalStockAvailable) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente. Disponible: " + finalStockAvailable));
                    }
                    existing.quantity += item.quantity;
                    existing.price = item.price;
                    return ResponseEntity.ok(cart);
                }
            }
            if (item.quantity > finalStockAvailable) {
                return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente. Disponible: " + finalStockAvailable));
            }
            cart.add(item);
            return ResponseEntity.ok(cart);
        }

        List<CartItem> cart = cartItemRepository.findByUserId(userToken);
        for (CartItem existing : cart) {
            if (existing.productId.equals(item.productId) && Objects.equals(existing.variantId, item.variantId)) {
                if (existing.quantity + item.quantity > finalStockAvailable) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente. Disponible: " + finalStockAvailable));
                }
                existing.quantity += item.quantity;
                existing.price = item.price;
                cartItemRepository.save(existing);
                return ResponseEntity.ok(cartItemRepository.findByUserId(userToken));
            }
        }

        if (item.quantity > finalStockAvailable) {
            return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente. Disponible: " + finalStockAvailable));
        }
        item.userId = userToken;
        cartItemRepository.save(item);
        return ResponseEntity.ok(cartItemRepository.findByUserId(userToken));
    }

    @PutMapping("/items/{productId}/increment")
    public ResponseEntity<?> increment(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                        @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                        @PathVariable String productId,
                                        @RequestParam(required = false) Long variantId) {
        return adjustQuantity(token, guestId, productId, variantId, 1);
    }

    @PutMapping("/items/{productId}/decrement")
    public ResponseEntity<?> decrement(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                        @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                        @PathVariable String productId,
                                        @RequestParam(required = false) Long variantId) {
        return adjustQuantity(token, guestId, productId, variantId, -1);
    }

    private ResponseEntity<?> adjustQuantity(String token, String guestId, String productId, Long variantId, int delta) {
        if (isAdminToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Los administradores no pueden usar el carrito"));
        }

        String userToken = resolveToken(token, guestId);

        if (isGuest(userToken)) {
            List<CartItem> cart = store.carts.getOrDefault(userToken, new ArrayList<>());
            CartItem item = cart.stream()
                    .filter(i -> i.productId.equals(productId) && Objects.equals(i.variantId, variantId))
                    .findFirst().orElse(null);
            if (item == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "El producto no está en el carrito"));
            }
            if (delta > 0) {
                Product product = productRepository.findById(productId).orElse(null);
                if (product == null || item.quantity + delta > availableStock(product, variantId)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente"));
                }
            }
            item.quantity += delta;
            if (item.quantity <= 0) {
                cart.remove(item);
            }
            store.carts.put(userToken, cart);
            return ResponseEntity.ok(withCurrentPrices(cart, false));
        }

        List<CartItem> cart = cartItemRepository.findByUserId(userToken);
        CartItem item = cart.stream()
                .filter(i -> i.productId.equals(productId) && Objects.equals(i.variantId, variantId))
                .findFirst().orElse(null);
        if (item == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El producto no está en el carrito"));
        }

        if (delta > 0) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null || item.quantity + delta > availableStock(product, variantId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente"));
            }
        }

        item.quantity += delta;
        if (item.quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            cartItemRepository.save(item);
        }
        return ResponseEntity.ok(withCurrentPrices(cartItemRepository.findByUserId(userToken), true));
    }

    /**
     * Pone al día el precio de cada línea con el precio vigente del producto (promoción incluida): si el admin
     * cambió o quitó una promoción después de agregar el producto, el carrito muestra lo que realmente se va a cobrar.
     */
    private List<CartItem> withCurrentPrices(List<CartItem> items, boolean persist) {
        for (CartItem item : items) {
            productRepository.findById(item.productId).ifPresent(product -> {
                double current = Pricing.effectivePrice(product);
                if (item.price != current) {
                    item.price = current;
                    if (persist) {
                        cartItemRepository.save(item);
                    }
                }
            });
        }
        return items;
    }

    @PostMapping("/remove")
    public ResponseEntity<?> remove(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                     @RequestBody CartItem item) {
        if (isAdminToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Los administradores no pueden usar el carrito"));
        }

        String userToken = resolveToken(token, guestId);

        if (isGuest(userToken)) {
            List<CartItem> list = store.carts.getOrDefault(userToken, new ArrayList<>());
            list.removeIf(i -> i.productId.equals(item.productId) && Objects.equals(i.variantId, item.variantId));
            store.carts.put(userToken, list);
            return ResponseEntity.ok(list);
        }

        List<CartItem> cart = cartItemRepository.findByUserId(userToken);
        List<CartItem> toDelete = cart.stream()
                .filter(i -> i.productId.equals(item.productId) && Objects.equals(i.variantId, item.variantId))
                .toList();
        cartItemRepository.deleteAll(toDelete);
        return ResponseEntity.ok(cartItemRepository.findByUserId(userToken));
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                       @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                       @RequestBody(required = false) Map<String, String> body) {
        if (isAdminToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Los administradores no pueden usar el carrito"));
        }

        String userToken = resolveToken(token, guestId);
        boolean guest = isGuest(userToken);

        List<CartItem> purchasedItems = guest
                ? store.carts.getOrDefault(userToken, new ArrayList<>())
                : cartItemRepository.findByUserId(userToken);

        for (CartItem item : purchasedItems) {
            Product product = productRepository.findById(item.productId).orElse(null);
            if (product == null || availableStock(product, item.variantId) < item.quantity) {
                String name = product != null ? product.name : item.productId;
                return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente para " + name));
            }
            item.price = Pricing.effectivePrice(product);
        }

        Map<String, String> shipping = body != null ? body : Map.of();
        String shippingName = shipping.getOrDefault("nombre", "").trim();
        String shippingAddress = shipping.getOrDefault("direccion", "").trim();
        String shippingCity = shipping.getOrDefault("ciudad", "").trim();
        String shippingPostalCode = shipping.getOrDefault("codigoPostal", "").trim();
        String shippingPhone = shipping.getOrDefault("telefono", "").trim();

        if (!guest && !purchasedItems.isEmpty()
                && (shippingName.isEmpty() || shippingAddress.isEmpty() || shippingCity.isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa los datos de envío (nombre, dirección y ciudad)"));
        }

        for (CartItem item : purchasedItems) {
            Product product = productRepository.findById(item.productId).orElse(null);
            product.stock -= item.quantity;
            productRepository.save(product);
            if (item.variantId != null) {
                ProductVariant variant = variantRepository.findById(item.variantId).orElse(null);
                if (variant != null) {
                    variant.stock -= item.quantity;
                    variantRepository.save(variant);
                }
            }
        }

        double subtotal = purchasedItems.stream().mapToDouble(i -> i.price * i.quantity).sum();
        double shippingCost = purchasedItems.isEmpty() ? 0 : SHIPPING_COST;

        String couponCode = shipping.getOrDefault("cupon", "").trim();
        double discountAmount = 0;
        Coupon appliedCoupon = null;
        if (!purchasedItems.isEmpty() && !couponCode.isBlank()) {
            appliedCoupon = couponRepository.findByCodeIgnoreCase(couponCode).orElse(null);
            if (appliedCoupon == null || !appliedCoupon.active || appliedCoupon.isExpired()
                    || !appliedCoupon.hasUsesLeft() || subtotal < appliedCoupon.minPurchase) {
                return ResponseEntity.badRequest().body(Map.of("error", "El cupón ya no es válido"));
            }
            discountAmount = appliedCoupon.computeDiscount(subtotal);
        }

        double total = subtotal + shippingCost - discountAmount;
        Long orderId = null;

        if (!purchasedItems.isEmpty()) {
            List<Order.OrderItem> orderItems = purchasedItems.stream()
                    .map(i -> new Order.OrderItem(i.productId, i.quantity, i.price, i.variant, i.variantId))
                    .toList();
            Order order = new Order(userToken, Instant.now(), orderItems, total, "COMPLETADA");
            order.subtotal = subtotal;
            order.shippingCost = shippingCost;
            order.discountAmount = discountAmount;
            order.couponCode = appliedCoupon != null ? appliedCoupon.code : null;
            order.shippingName = shippingName;
            order.shippingAddress = shippingAddress;
            order.shippingCity = shippingCity;
            order.shippingPostalCode = shippingPostalCode;
            order.shippingPhone = shippingPhone;
            orderRepository.save(order);
            orderId = order.id;

            if (appliedCoupon != null) {
                appliedCoupon.usesCount += 1;
                couponRepository.save(appliedCoupon);
            }

            if (!guest) {
                String email = jwtUtil.extractEmail(token);
                notificationService.notify(email, "Tu compra de " + purchasedItems.size() + " producto(s) se realizó con éxito.");
            }
        }

        if (guest) {
            store.carts.remove(userToken);
        } else {
            cartItemRepository.deleteAll(purchasedItems);
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("ok", true);
        response.put("items", purchasedItems);
        response.put("subtotal", subtotal);
        response.put("shippingCost", shippingCost);
        response.put("discountAmount", discountAmount);
        response.put("couponCode", appliedCoupon != null ? appliedCoupon.code : null);
        response.put("total", total);
        response.put("orderId", orderId);
        return ResponseEntity.ok(response);
    }
}
