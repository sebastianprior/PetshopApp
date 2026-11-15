package com.petshop.app.service;

import com.petshop.app.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Salchi Bot: con el catálogo real, cada pedido en lenguaje natural debe terminar en productos que tengan sentido. */
class ChatAdvisorTest {

    private List<Product> catalog;
    private ChatAdvisor advisor;

    private static Product product(String id, String name, String brand, double price, Double oldPrice, double rating, String badge, String category, int stock) {
        return new Product(id, name, brand, price, oldPrice, rating, "/products/" + id + ".svg", badge, category, stock);
    }

    @BeforeEach
    void setUp() {
        catalog = new ArrayList<>(List.of(
                product("p1", "Croquetas SuperDog 10kg", "SuperDog", 12999, 15999.0, 4.6, "Oferta", "alimentos", 50),
                product("p2", "Pelota Squeaky", "PetPlay", 499, null, 4.3, null, "juguetes", 200),
                product("p3", "Collar Comfort L", "WalkEasy", 899, 1099.0, 4.5, "Más vendido", "accesorios", 120),
                product("p4", "Arena para gato Premium 8L", "GatoClean", 649, 799.0, 4.4, "Oferta", "alimentos", 80),
                product("p5", "Snack Dental Mini", "BiteCare", 699, 899.0, 4.7, "Nuevo", "salud", 140),
                product("p6", "Cepillo para perros", "PetClean", 850, 1100.0, 4.4, "Oferta", "higiene", 90),
                product("p7", "Rascador para gatos", "CatJoy", 2499, null, 4.8, "Top", "juguetes", 60),
                product("p8", "Juguete masticable", "ChewFun", 1199, 1499.0, 4.6, "Más vendido", "juguetes", 180),
                product("p9", "Pasta dental para perros", "DogSmile", 999, null, 4.5, null, "salud", 110),
                product("p10", "Cama para perro mediana", "CozyNest", 4599, 5200.0, 4.7, "Oferta", "accesorios", 35),
                product("p11", "Comida húmeda gatitos 3kg", "CatBites", 3299, 3899.0, 4.6, "Nuevo", "alimentos", 75),
                product("p12", "Peluche de lana para perro", "PawJoy", 799, 1099.0, 4.3, null, "juguetes", 170),
                product("p13", "Arnés de paseo pequeño", "TrailFit", 1599, 1899.0, 4.5, "Popular", "accesorios", 130),
                product("p14", "Shampoo antipulgas", "FreshPaw", 1299, 1499.0, 4.7, "Top", "higiene", 88),
                product("p15", "Vitaminas para perros", "VitalPet", 1799, null, 4.8, "Recomendado", "salud", 95),
                product("p16", "Bowl doble antiresbalón", "FeederMax", 1099, 1399.0, 4.4, "Oferta", "accesorios", 160),
                product("p17", "Cucha para gatos pequeña", "CatNest", 3599, 4199.0, 4.7, "Oferta", "accesorios", 50),
                product("p19", "Toallitas húmedas para mascotas", "CleanPaw", 899, null, 4.2, null, "higiene", 190),
                product("p20", "Suplemento articular", "JointCare", 2099, 2499.0, 4.9, "Top", "salud", 78),
                product("p21", "Comedero automático", "PetAuto", 4999, 5899.0, 4.8, "Nuevo", "accesorios", 42),
                product("p22", "Tirador para gatos", "KittieFun", 699, 899.0, 4.5, "Eco", "juguetes", 150),
                product("p24", "Limpieza de oído perros", "EarSafe", 1499, 1899.0, 4.7, "Recomendado", "salud", 86),
                product("p27", "Pañales para cachorros", "PuppyCare", 1199, 1499.0, 4.5, "Oferta", "higiene", 100),
                product("p28", "Mouse para gatos", "CatMouse", 499, null, 4.6, "Popular", "juguetes", 210),
                product("p32", "Bolsa de comida premium para gatos", "KittyBest", 5499, 6599.0, 4.8, "Top", "alimentos", 58),
                product("p33", "Correa reflectante 1.5m", "NightWalk", 1299, 1699.0, 4.5, "Nuevo", "accesorios", 118),
                product("p34", "Aditivo calmante para perros", "CalmPet", 1899, 2299.0, 4.7, "Recomendado", "salud", 81),
                product("p36", "Pipeta antipulgas mediana", "FleaStop", 2499, 2999.0, 4.9, "Más vendido", "salud", 92),
                product("p43", "Bocaditos de pollo para cachorros", "PuppyCrunch", 1299, 1699.0, 4.6, "Oferta", "alimentos", 170),
                product("p45", "Tobillera anti-ladrido", "SilentPaw", 1999, 2499.0, 4.4, "Nuevo", "salud", 90),
                product("p49", "Canasta portable para viajes", "TravelPet", 3999, 4599.0, 4.6, "Nuevo", "accesorios", 61),
                product("p50", "Comida húmeda para perros senior", "SeniorBites", 3699, 4399.0, 4.7, "Recomendado", "alimentos", 67)));
        advisor = new ChatAdvisor(catalog, FaqDefaults.items().stream().map(i -> new FaqMatcher.Entry(i.question, i.answer)).toList());
    }

    private List<String> ids(String message) {
        return advisor.advise(message, List.of()).productIds();
    }

    @Test
    void aCatThatGetsBoredIsOfferedCatToysAndNeverDogPlush() {
        List<String> picks = ids("mi gato se aburre");

        assertThat(picks).isNotEmpty().allMatch(id -> List.of("p22", "p28", "p7", "p8", "p2").contains(id));
        assertThat(picks).doesNotContain("p12");
        assertThat(picks.get(0)).isIn("p22", "p28");
    }

    @Test
    void walkingSuggestsLeashHarnessAndCollar() {
        assertThat(ids("busco algo para pasear a mi perro")).contains("p13", "p33").hasSizeLessThanOrEqualTo(3);
        assertThat(ids("un arnés").get(0)).isEqualTo("p13");
        assertThat(ids("necesito una correa")).startsWith("p33");
    }

    @Test
    void fleasPointToTheFleaProducts() {
        assertThat(ids("mi perro tiene pulgas")).contains("p36", "p14").doesNotContain("p12");
        assertThat(ids("mi perro tiene pulgas").get(0)).isIn("p36", "p14");
    }

    @Test
    void foodForKittensFindsCatFoodAndSkipsDogFood() {
        List<String> picks = ids("comida para gatitos");

        assertThat(picks.get(0)).isEqualTo("p11");
        assertThat(picks).doesNotContain("p50", "p1", "p43");
    }

    @Test
    void puppyFoodAndSeniorFoodAreTellApart() {
        assertThat(ids("snacks para mi cachorro")).contains("p43");
        assertThat(ids("comida para mi perro viejo").get(0)).isEqualTo("p50");
    }

    @Test
    void sleepingSuggestsBedsForTheRightSpecies() {
        assertThat(ids("mi perro necesita dormir").get(0)).isEqualTo("p10");
        assertThat(ids("una cama para mi gato").get(0)).isEqualTo("p17");
    }

    @Test
    void dentalCareAndOtherHealthNeedsAreUnderstood() {
        assertThat(ids("mi perro tiene mal aliento")).contains("p9");
        assertThat(ids("le duelen las articulaciones")).contains("p20");
        assertThat(ids("tiene los oídos sucios")).contains("p24");
        assertThat(ids("mi perro está muy nervioso")).contains("p34");
        assertThat(ids("ladra mucho")).contains("p45");
    }

    @Test
    void travelLitterAndDiapersAreFound() {
        assertThat(ids("voy a viajar con mi perro")).contains("p49");
        assertThat(ids("arena para mi gato").get(0)).isEqualTo("p4");
        assertThat(ids("pañales")).contains("p27");
    }

    @Test
    void aBudgetFiltersOutExpensiveProducts() {
        List<String> picks = ids("un juguete hasta $800");

        assertThat(picks).isNotEmpty();
        picks.forEach(id -> assertThat(catalog.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow().price).isLessThanOrEqualTo(800));
    }

    @Test
    void askingForOffersReturnsDiscountedProductsCheapestFirst() {
        ChatAdvisor.Advice advice = advisor.advise("¿qué ofertas hay para mi perro?", List.of());

        assertThat(advice.productIds()).isNotEmpty();
        advice.productIds().forEach(id -> assertThat(catalog.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow().oldPrice).isNotNull());
    }

    @Test
    void puppyFoodRequestDoesNotSuggestDiapersOrSeniorFood() {
        List<String> picks = ids("mi cachorro tiene que comer");

        assertThat(picks).contains("p43").doesNotContain("p27", "p50");
    }

    @Test
    void barkingAndBadWalkingPointToTheRightProducts() {
        assertThat(ids("algo para que mi perro no ladre")).contains("p45");
        assertThat(ids("mi perro viejo no puede caminar bien")).contains("p20");
    }

    @Test
    void catSpecificToysComeBeforeNeutralOnes() {
        assertThat(ids("mi gato se aburre").subList(0, 2)).containsExactlyInAnyOrder("p22", "p28");
    }

    @Test
    void theBiggestDiscountsComeFirstWhenAskingForOffers() {
        List<String> picks = ids("qué ofertas hay");

        assertThat(picks).hasSize(3);
        double previous = Double.MAX_VALUE;
        for (String id : picks) {
            Product p = catalog.stream().filter(c -> c.id.equals(id)).findFirst().orElseThrow();
            double discount = (p.oldPrice - p.price) / p.oldPrice;
            assertThat(discount).isLessThanOrEqualTo(previous + 0.025);
            previous = discount;
        }
    }

    @Test
    void theBotRemembersThePetFromEarlierMessages() {
        List<String> picks = advisor.advise("algo para que se divierta", List.of("tengo un gato")).productIds();

        assertThat(picks).isNotEmpty().doesNotContain("p12");
        assertThat(picks.get(0)).isIn("p22", "p28");
    }

    @Test
    void mentioningOnlyThePetShowsFeaturedProductsForIt() {
        ChatAdvisor.Advice advice = advisor.advise("tengo un gato", List.of());

        assertThat(advice.productIds()).isNotEmpty().doesNotContain("p12", "p10", "p50");
        assertThat(advice.reply()).contains("gato");
    }

    @Test
    void greetingsThanksAndQuestionsAboutTheStoreGetPlainAnswers() {
        assertThat(advisor.advise("hola", List.of()).productIds()).isEmpty();
        assertThat(advisor.advise("hola", List.of()).reply()).contains("Salchi Bot");
        assertThat(advisor.advise("muchas gracias", List.of()).reply()).contains("De nada");
        assertThat(advisor.advise("cuánto cuesta el envío?", List.of()).reply()).contains("costo").contains("carrito");
        assertThat(advisor.advise("hacen devoluciones?", List.of()).reply()).contains("Mis pedidos");
        assertThat(advisor.advise("¿en cuántas cuotas puedo pagar?", List.of()).reply()).contains("6 cuotas");
    }

    @Test
    void whenNothingMatchesItAsksForMoreAndShowsTopProducts() {
        ChatAdvisor.Advice advice = advisor.advise("xyzzy plugh", List.of());

        assertThat(advice.reply()).contains("Contame");
        assertThat(advice.productIds()).hasSize(3);
        assertThat(advice.suggestions()).isNotEmpty();
    }

    @Test
    void outOfStockProductsAreNeverRecommended() {
        catalog.stream().filter(p -> p.id.equals("p36") || p.id.equals("p14")).forEach(p -> p.stock = 0);

        assertThat(new ChatAdvisor(catalog).advise("mi perro tiene pulgas", List.of()).productIds()).doesNotContain("p36", "p14");
    }

    @Test
    void emptyOrHugeMessagesAreHandled() {
        assertThat(advisor.advise("", List.of()).reply()).contains("Salchi Bot");
        assertThat(advisor.advise(null, null).reply()).contains("Salchi Bot");
        assertThat(advisor.advise("gato ".repeat(500), List.of()).productIds()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void textIsNormalizedAndPluralsMatch() {
        assertThat(ChatAdvisor.normalize("¡Pañales, CÓMODOS!")).isEqualTo("panales comodos");
        assertThat(ChatAdvisor.stem("juguetes")).isEqualTo("juguete");
        assertThat(ChatAdvisor.stem("pañales".replace("ñ", "n"))).isEqualTo("panal");
        assertThat(ChatAdvisor.stem("gatos")).isEqualTo("gato");
        assertThat(ChatAdvisor.stem("collares")).isEqualTo("collar");
    }

    @Test
    void frequentQuestionsAreAnsweredWithTheAdminsTextAndSuggestOtherQuestions() {
        ChatAdvisor.Advice advice = advisor.advise("¿cuánto tarda en llegar mi pedido?", List.of());

        assertThat(advice.productIds()).isEmpty();
        assertThat(advice.reply()).contains("24 hs");
        assertThat(advice.suggestions()).hasSize(3).doesNotContain("¿Cuánto tarda en llegar mi pedido?");
    }

    @Test
    void aProductRequestBeatsAWeakFaqMatch() {
        ChatAdvisor.Advice advice = advisor.advise("mi perro necesita comida, hacen envíos?", List.of());

        assertThat(advice.productIds()).isNotEmpty();
    }

    @Test
    void askingForOffersStillRecommendsProductsInsteadOfTheCouponQuestion() {
        ChatAdvisor.Advice advice = advisor.advise("¿Qué ofertas hay?", List.of());

        assertThat(advice.productIds()).isNotEmpty();
    }

    @Test
    void askingForTheFaqListShowsTheQuestionsAsSuggestions() {
        ChatAdvisor.Advice advice = advisor.advise("preguntas frecuentes", List.of());

        assertThat(advice.reply()).contains("preguntas");
        assertThat(advice.suggestions()).hasSize(6);
        // tocar una sugerencia (se envía la pregunta tal cual) devuelve su respuesta
        assertThat(advisor.advise(advice.suggestions().get(4), List.of()).reply()).isNotBlank().doesNotContain("Contame");
    }

    @Test
    void withoutFrequentQuestionsTheBotDoesNotInventStoreAnswers() {
        ChatAdvisor bare = new ChatAdvisor(catalog);

        ChatAdvisor.Advice advice = bare.advise("¿cuánto tarda el envío?", List.of());

        assertThat(advice.reply()).doesNotContain("24 hs").doesNotContain("cuotas");
    }
}
