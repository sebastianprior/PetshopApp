package com.petshop.app.service;

import com.petshop.app.model.Category;
import com.petshop.app.model.Product;
import com.petshop.app.model.User;
import com.petshop.app.repository.CategoryRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DatabaseSeeder(CategoryRepository categoryRepository,
                          ProductRepository productRepository,
                          UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        saveCategoryIfMissing(new Category("perros", "Perros", "#FFD6A5"));
        saveCategoryIfMissing(new Category("gatos", "Gatos", "#BFEAF5"));
        saveCategoryIfMissing(new Category("alimentos", "Alimentos", "#FFE6CC"));
        saveCategoryIfMissing(new Category("juguetes", "Juguetes", "#FDE2E4"));
        saveCategoryIfMissing(new Category("accesorios", "Accesorios", "#E9D5FF"));
        saveCategoryIfMissing(new Category("higiene", "Higiene", "#D1FAE5"));
        saveCategoryIfMissing(new Category("salud", "Salud", "#DBEAFE"));

        saveProductIfMissing(new Product("p1", "Croquetas SuperDog 10kg", "SuperDog", 12999.0, 15999.0, 4.6, productImage("p1"), "Oferta", "alimentos", 50));
        saveProductIfMissing(new Product("p2", "Pelota Squeaky", "PetPlay", 499.0, null, 4.3, productImage("p2"), null, "juguetes", 200));
        saveProductIfMissing(new Product("p3", "Collar Comfort L", "WalkEasy", 899.0, 1099.0, 4.5, productImage("p3"), "Más vendido", "accesorios", 120));
        saveProductIfMissing(new Product("p4", "Arena para gato Premium 8L", "GatoClean", 649.0, 799.0, 4.4, productImage("p4"), "Oferta", "alimentos", 80));
        saveProductIfMissing(new Product("p5", "Snack Dental Mini", "BiteCare", 699.0, 899.0, 4.7, productImage("p5"), "Nuevo", "salud", 140));
        saveProductIfMissing(new Product("p6", "Cepillo para perros", "PetClean", 850.0, 1100.0, 4.4, productImage("p6"), "Oferta", "higiene", 90));
        saveProductIfMissing(new Product("p7", "Rascador para gatos", "CatJoy", 2499.0, null, 4.8, productImage("p7"), "Top", "juguetes", 60));
        saveProductIfMissing(new Product("p8", "Juguete masticable", "ChewFun", 1199.0, 1499.0, 4.6, productImage("p8"), "Más vendido", "juguetes", 180));
        saveProductIfMissing(new Product("p9", "Pasta dental para perros", "DogSmile", 999.0, null, 4.5, productImage("p9"), null, "salud", 110));
        saveProductIfMissing(new Product("p10", "Cama para perro mediana", "CozyNest", 4599.0, 5200.0, 4.7, productImage("p10"), "Oferta", "accesorios", 35));
        saveProductIfMissing(new Product("p11", "Comida húmeda gatitos 3kg", "CatBites", 3299.0, 3899.0, 4.6, productImage("p11"), "Nuevo", "alimentos", 75));
        saveProductIfMissing(new Product("p12", "Peluche de lana para perro", "PawJoy", 799.0, 1099.0, 4.3, productImage("p12"), null, "juguetes", 170));
        saveProductIfMissing(new Product("p13", "Arnés de paseo pequeño", "TrailFit", 1599.0, 1899.0, 4.5, productImage("p13"), "Popular", "accesorios", 130));
        saveProductIfMissing(new Product("p14", "Shampoo antipulgas", "FreshPaw", 1299.0, 1499.0, 4.7, productImage("p14"), "Top", "higiene", 88));
        saveProductIfMissing(new Product("p15", "Vitaminas para perros", "VitalPet", 1799.0, null, 4.8, productImage("p15"), "Recomendado", "salud", 95));
        saveProductIfMissing(new Product("p16", "Bowl doble antiresbalón", "FeederMax", 1099.0, 1399.0, 4.4, productImage("p16"), "Oferta", "accesorios", 160));
        saveProductIfMissing(new Product("p17", "Cucha para gatos pequeña", "CatNest", 3599.0, 4199.0, 4.7, productImage("p17"), "Oferta", "accesorios", 50));
        saveProductIfMissing(new Product("p18", "Hueso masticable XXL", "ChewPro", 1499.0, 1899.0, 4.6, productImage("p18"), "Nuevo", "juguetes", 110));
        saveProductIfMissing(new Product("p19", "Toallitas húmedas para mascotas", "CleanPaw", 899.0, null, 4.2, productImage("p19"), null, "higiene", 190));
        saveProductIfMissing(new Product("p20", "Suplemento articular", "JointCare", 2099.0, 2499.0, 4.9, productImage("p20"), "Top", "salud", 78));
        saveProductIfMissing(new Product("p21", "Comedero automático", "PetAuto", 4999.0, 5899.0, 4.8, productImage("p21"), "Nuevo", "accesorios", 42));
        saveProductIfMissing(new Product("p22", "Tirador para gatos", "KittieFun", 699.0, 899.0, 4.5, productImage("p22"), "Eco", "juguetes", 150));
        saveProductIfMissing(new Product("p23", "Snacks naturales pollo", "BarkBites", 1099.0, null, 4.6, productImage("p23"), "Nuevo", "alimentos", 140));
        saveProductIfMissing(new Product("p24", "Limpieza de oído perros", "EarSafe", 1499.0, 1899.0, 4.7, productImage("p24"), "Recomendado", "salud", 86));
        saveProductIfMissing(new Product("p25", "Guante para cepillar", "SoftBrush", 559.0, 799.0, 4.3, productImage("p25"), "Oferta", "higiene", 175));
        saveProductIfMissing(new Product("p26", "Lazo de seguridad", "SafeWalk", 899.0, 1199.0, 4.4, productImage("p26"), null, "accesorios", 120));
        saveProductIfMissing(new Product("p27", "Pañales para cachorros", "PuppyCare", 1199.0, 1499.0, 4.5, productImage("p27"), "Oferta", "higiene", 100));
        saveProductIfMissing(new Product("p28", "Mouse para gatos", "CatMouse", 499.0, null, 4.6, productImage("p28"), "Popular", "juguetes", 210));
        saveProductIfMissing(new Product("p29", "Pasta dental gatos", "FelineSmile", 1099.0, null, 4.5, productImage("p29"), null, "salud", 96));
        saveProductIfMissing(new Product("p30", "Pañuelo absorbente", "PetWipe", 799.0, 999.0, 4.2, productImage("p30"), null, "higiene", 180));
        saveProductIfMissing(new Product("p31", "Corderito de felpa para perros", "CozyPaws", 1399.0, 1799.0, 4.4, productImage("p31"), "Oferta", "juguetes", 146));
        saveProductIfMissing(new Product("p32", "Bolsa de comida premium para gatos", "KittyBest", 5499.0, 6599.0, 4.8, productImage("p32"), "Top", "alimentos", 58));
        saveProductIfMissing(new Product("p33", "Correa reflectante 1.5m", "NightWalk", 1299.0, 1699.0, 4.5, productImage("p33"), "Nuevo", "accesorios", 118));
        saveProductIfMissing(new Product("p34", "Aditivo calmante para perros", "CalmPet", 1899.0, 2299.0, 4.7, productImage("p34"), "Recomendado", "salud", 81));
        saveProductIfMissing(new Product("p35", "Champú hipoalergénico", "GentlePets", 1099.0, 1399.0, 4.6, productImage("p35"), "Oferta", "higiene", 130));
        saveProductIfMissing(new Product("p36", "Pipeta antipulgas mediana", "FleaStop", 2499.0, 2999.0, 4.9, productImage("p36"), "Más vendido", "salud", 92));
        saveProductIfMissing(new Product("p37", "Comedero de acero inoxidable", "SteelFeeder", 1799.0, 2399.0, 4.4, productImage("p37"), "Eco", "accesorios", 150));
        saveProductIfMissing(new Product("p38", "Lanzador de pelotas", "FetchPro", 2299.0, 2899.0, 4.6, productImage("p38"), "Nuevo", "juguetes", 112));
        saveProductIfMissing(new Product("p39", "Snacks de pescado para gatos", "SeaBites", 1199.0, 1499.0, 4.5, productImage("p39"), "Oferta", "alimentos", 132));
        saveProductIfMissing(new Product("p40", "Toalla de microfibra para mascotas", "DryPaw", 999.0, 1299.0, 4.3, productImage("p40"), null, "higiene", 160));
        saveProductIfMissing(new Product("p41", "Casa de madera para gatos", "CatCastle", 6499.0, 7599.0, 4.8, productImage("p41"), "Top", "accesorios", 34));
        saveProductIfMissing(new Product("p42", "Paseadores de goma para perros", "GripWalk", 899.0, 1199.0, 4.2, productImage("p42"), null, "accesorios", 140));
        saveProductIfMissing(new Product("p43", "Bocaditos de pollo para cachorros", "PuppyCrunch", 1299.0, 1699.0, 4.6, productImage("p43"), "Oferta", "alimentos", 170));
        saveProductIfMissing(new Product("p44", "Rascador vertical plegable", "ClawTower", 2899.0, 3499.0, 4.7, productImage("p44"), "Más vendido", "juguetes", 76));
        saveProductIfMissing(new Product("p45", "Tobillera anti-ladrido", "SilentPaw", 1999.0, 2499.0, 4.4, productImage("p45"), "Nuevo", "salud", 90));
        saveProductIfMissing(new Product("p46", "Pañales especiales para gatos", "CleanCat", 1399.0, 1799.0, 4.3, productImage("p46"), "Oferta", "higiene", 125));
        saveProductIfMissing(new Product("p47", "Mordedor de silicona", "BiteSoft", 899.0, 1199.0, 4.5, productImage("p47"), "Popular", "juguetes", 180));
        saveProductIfMissing(new Product("p48", "Suplemento omega para mascotas", "OmegaPet", 2199.0, 2599.0, 4.8, productImage("p48"), "Top", "salud", 70));
        saveProductIfMissing(new Product("p49", "Canasta portable para viajes", "TravelPet", 3999.0, 4599.0, 4.6, productImage("p49"), "Nuevo", "accesorios", 61));
        saveProductIfMissing(new Product("p50", "Comida húmeda para perros senior", "SeniorBites", 3699.0, 4399.0, 4.7, productImage("p50"), "Recomendado", "alimentos", 67));

        saveUserIfMissing(new User("u1", "cliente@ejemplo.com", "password", "Cliente Demo"));

        User admin = new User("u2", "admin@petshop.com", "admin123", "Administrador");
        admin.role = "ADMIN";
        saveUserIfMissing(admin);

        saveUserIfMissing(new User("u3", "juan@petshop.com", "123456", "Juan Petshop"));
    }

    /** Cada producto tiene su propia ilustración (src/app/public/products/<id>.svg), que representa lo que dice el título. */
    private String productImage(String productId) {
        return "/products/" + productId + ".svg";
    }

    private void saveCategoryIfMissing(Category category) {
        if (!categoryRepository.existsById(category.id)) {
            categoryRepository.save(category);
        }
    }

    private void saveProductIfMissing(Product product) {
        if (productRepository.existsById(product.id)) {
            productRepository.findById(product.id).ifPresent(existing -> {
                boolean needsRefresh = existing.imageUrl == null
                        || existing.imageUrl.isBlank()
                        || existing.imageUrl.startsWith("/images/")
                        || !existing.imageUrl.equals(product.imageUrl);

                if (needsRefresh) {
                    existing.imageUrl = product.imageUrl;
                    existing.name = product.name;
                    existing.brand = product.brand;
                    existing.price = product.price;
                    existing.oldPrice = product.oldPrice;
                    existing.rating = product.rating;
                    existing.badge = product.badge;
                    existing.categoryId = product.categoryId;
                    existing.stock = product.stock;
                    productRepository.save(existing);
                }
            });
            return;
        }

        productRepository.save(product);
    }

    private void saveUserIfMissing(User user) {
        var existing = userRepository.findByEmail(user.email);
        if (existing.isEmpty()) {
            user.password = passwordEncoder.encode(user.password);
            userRepository.save(user);
            return;
        }

        User existingUser = existing.get();
        boolean needsRefresh = !existingUser.password.startsWith("$2") || !existingUser.role.equals(user.role);
        if (needsRefresh) {
            if (!existingUser.password.startsWith("$2")) {
                existingUser.password = passwordEncoder.encode(existingUser.password);
            }
            existingUser.role = user.role;
            userRepository.save(existingUser);
        }
    }
}
