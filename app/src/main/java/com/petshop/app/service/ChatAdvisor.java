package com.petshop.app.service;

import com.petshop.app.model.Product;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cerebro de Salchi Bot: entiende lo que escribe el cliente y recomienda productos del catálogo.
 *
 * No usa un modelo de lenguaje externo: normaliza el texto, detecta la mascota (perro / gato), la necesidad
 * ("se aburre", "tiene pulgas", "quiero algo para pasear") y un presupuesto opcional, y puntúa cada producto del
 * catálogo. Es determinista, funciona sin conexión y sin claves de API.
 */
public class ChatAdvisor {

    /** Respuesta del bot: texto, ids de productos recomendados (en orden) y sugerencias para seguir la charla. */
    public record Advice(String reply, List<String> productIds, List<String> suggestions) {}

    private static final int MAX_PRODUCTS = 3;
    private static final int MIN_SCORE = 4;
    private static final int MAX_MESSAGE_LENGTH = 300;

    private static final Set<String> STOPWORDS = Set.of(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "para", "por", "con", "que", "quiero", "busco",
            "necesito", "algo", "mi", "mis", "me", "tu", "y", "o", "en", "a", "es", "hay", "tenes", "tienen", "tiene", "quisiera",
            "queria", "comprar", "favor", "muy", "mas", "menos", "hasta", "como", "cual", "cuales", "del", "al", "lo", "se", "si",
            "no", "te", "le", "ese", "esa", "esto", "estoy", "estas", "ser", "son", "tengo", "puedo", "podes", "dame", "mostrame",
            "recomenda", "recomendas", "recomiendas", "sugeri", "sugeris", "ayuda", "ayudame", "gustaria", "pesos", "peso", "mucho", "mucha");

    private static final Set<String> DOG_WORDS = Set.of("perro", "perrito", "perra", "perrita", "cachorro", "cachorrito", "can", "canino", "labrador");
    /** "perro" y "gato" aparecen en muchísimos nombres: no alcanza con eso para recomendar. */
    private static final Set<String> GENERIC_SPECIES = Set.of("perro", "gato");
    private static final Set<String> CAT_WORDS = Set.of("gato", "gatito", "gata", "gatita", "minino", "felino", "michi");

    // Todas las palabras están en su forma sin plural (la misma que produce stem()).
    private static final Set<String> GREETINGS = Set.of("hola", "buena", "bueno", "buen", "dia", "tarde", "noche", "hey", "holi", "ola", "que", "tal", "saludo");
    private static final Set<String> THANKS = Set.of("gracia", "genial", "buenisimo", "perfecto", "excelente", "listo", "dale", "ok", "joya", "barbaro");

    /** disparador (raíz de palabra) -> raíces de nombres de producto que sugiere, con su peso. */
    private static final Map<String, Map<String, Integer>> NEEDS = new LinkedHashMap<>();

    /** disparador -> categoría a reforzar. */
    private static final Map<String, String> CATEGORY_HINTS = new LinkedHashMap<>();

    /** disparador -> frase para armar la respuesta. */
    private static final Map<String, String> NEED_PHRASES = new LinkedHashMap<>();

    private static void need(String phrase, String[] triggers, Object... productWeights) {
        // Las raíces se guardan "sin plural" igual que las palabras del cliente y de los nombres ("arnés" -> "arne").
        Map<String, Integer> targets = new LinkedHashMap<>();
        for (int i = 0; i < productWeights.length; i += 2) {
            targets.put(stem((String) productWeights[i]), (Integer) productWeights[i + 1]);
        }
        for (String trigger : triggers) {
            NEEDS.put(stem(trigger), targets);
            NEED_PHRASES.put(stem(trigger), phrase);
        }
    }

    static {
        need("la comida", new String[] {"comida", "comer", "hambre", "alimento", "alimentar", "alimentacion", "nutricion", "bolsa"},
                "comida", 5, "croqueta", 5, "bolsa", 2, "snack", 1, "bocadito", 2);
        need("un premio", new String[] {"premio", "golosina", "snack", "galleta", "rico", "recompensa", "bocadito"},
                "snack", 5, "bocadito", 5, "hueso", 2);
        need("la hora de comer", new String[] {"plato", "comedero", "bowl", "agua", "vacacion", "automatico"},
                "comedero", 5, "bowl", 5, "automatico", 5);
        need("la diversión", new String[] {"jugar", "juega", "juego", "juguete", "aburr", "divierte", "divierta", "diversion", "divertir", "aburrido", "entreten", "divertir", "energia", "hiperactivo", "destruye", "muerde", "morder", "mordedor", "masticar", "pelota", "peluche"},
                "juguete", 5, "pelota", 4, "mordedor", 5, "peluche", 4, "mouse", 4, "tirador", 4, "lanzador", 4, "hueso", 3, "corderito", 3, "rascador", 2);
        need("las pulgas", new String[] {"pulga", "garrapata", "antipulga", "parasito", "desparasitar", "rasca"},
                "antipulga", 6, "pipeta", 6, "shampoo", 3);
        need("el baño y la limpieza", new String[] {"bano", "banar", "shampoo", "champu", "lavar", "limpiar", "limpieza", "higiene", "sucio", "olor", "oler", "ducha"},
                "shampoo", 5, "champu", 5, "toallita", 4, "toalla", 4, "panuelo", 3, "limpieza", 2);
        need("el pelaje", new String[] {"pelo", "pelaje", "cepillar", "cepillo", "muda", "nudo", "peinar"},
                "cepillo", 5, "guante", 5);
        need("los dientes", new String[] {"diente", "dental", "sarro", "aliento", "boca", "muela"},
                "dental", 6, "pasta", 5, "snack", 2);
        need("los oídos", new String[] {"oido", "oreja", "otitis"},
                "oido", 6, "limpieza", 3);
        need("las articulaciones", new String[] {"articulacion", "articular", "cojea", "cojera", "rigido", "artrosis", "huesos"},
                "articular", 6, "suplemento", 3);
        need("la calma", new String[] {"calmar", "calma", "calmante", "ansiedad", "nervioso", "miedo", "estres", "tranquilo", "petardo", "ladra", "ladre", "ladren", "ladrido", "ladrar"},
                "calmante", 6, "aditivo", 3, "ladrido", 5);
        need("las vitaminas", new String[] {"vitamina", "defensa", "suplemento", "omega", "brillo", "inmune"},
                "vitamina", 6, "suplemento", 4, "omega", 5);
        need("los paseos", new String[] {"paseo", "pasear", "caminar", "salir", "calle", "correa", "collar", "arnes", "tirar"},
                "correa", 5, "collar", 5, "arnes", 5, "lazo", 4, "paseador", 3, "reflectante", 2);
        need("el descanso", new String[] {"dormir", "descansar", "cama", "cucha", "casa", "frio", "cobijo", "siesta", "colchon"},
                "cama", 6, "cucha", 6, "casa", 5);
        need("los viajes", new String[] {"viaje", "viajar", "transportar", "auto", "vuelo", "traslado", "transportadora", "canasta"},
                "canasta", 6, "portable", 4, "viaje", 5);
        need("la arena", new String[] {"arena", "bandeja", "sanitario", "inodoro"},
                "arena", 6);
        need("los pañales", new String[] {"panal", "pis", "pipi", "incontinencia", "celo", "accidente", "orina"},
                "panal", 6, "toallita", 2);
        need("el rascado", new String[] {"rascar", "rascador", "arana", "muebles", "sillon", "sofa"},
                "rascador", 6);
        need("la noche", new String[] {"reflectante", "noche", "oscuro", "visibilidad"},
                "reflectante", 6, "correa", 3);

        CATEGORY_HINTS.put(stem("juguete"), "juguetes");
        CATEGORY_HINTS.put(stem("jugar"), "juguetes");
        CATEGORY_HINTS.put(stem("aburr"), "juguetes");
        CATEGORY_HINTS.put(stem("higiene"), "higiene");
        CATEGORY_HINTS.put(stem("bano"), "higiene");
        CATEGORY_HINTS.put(stem("salud"), "salud");
        CATEGORY_HINTS.put(stem("vitamina"), "salud");
        CATEGORY_HINTS.put(stem("accesorio"), "accesorios");
        CATEGORY_HINTS.put(stem("alimento"), "alimentos");
        CATEGORY_HINTS.put(stem("comida"), "alimentos");
    }

    private static final Pattern BUDGET = Pattern.compile("(?:menos de|hasta|maximo|no mas de|por debajo de|tope|presupuesto de|gasto)\\s*\\$?\\s*(\\d{3,6})|\\$\\s*(\\d{3,6})|(\\d{3,6})\\s*(?:pesos|\\$)");

    private final List<Product> catalog;

    public ChatAdvisor(List<Product> catalog) {
        this.catalog = catalog.stream().filter(p -> p.stock > 0).toList();
    }

    public Advice advise(String rawMessage, List<String> history) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        if (message.isEmpty()) {
            return intro();
        }

        String normalized = normalize(message);
        List<String> tokens = tokenize(normalized);

        Advice faq = answerFaq(normalized, tokens);
        if (faq != null) {
            return faq;
        }

        Set<String> triggers = triggersIn(tokens);
        if (normalized.contains("caminar bien") || normalized.contains("cuesta caminar") || normalized.contains("no camina") || normalized.contains("renguea")) {
            triggers.add(stem("articular"));
        }
        boolean hasNeed = !triggers.isEmpty();
        Species species = speciesOf(tokens);
        if (species == Species.NONE && history != null) {
            // "para mi gato" y después "algo barato": se recuerda la mascota de los mensajes anteriores
            for (int i = history.size() - 1; i >= 0 && species == Species.NONE; i--) {
                species = speciesOf(tokenize(normalize(history.get(i))));
            }
        }
        Integer budget = budgetOf(normalized);
        boolean cheap = tokens.stream().anyMatch(t -> Set.of("barato", "economico", "accesible", "ahorrar", "oferta", "descuento", "promo", "promocion").contains(t));
        boolean wantsOffers = tokens.stream().anyMatch(t -> Set.of("oferta", "descuento", "promo", "promocion", "rebaja").contains(t));
        boolean puppy = tokens.contains("cachorro") || tokens.contains("cachorrito") || tokens.contains("bebe");
        boolean senior = tokens.contains("senior") || tokens.contains("viejo") || tokens.contains("mayor") || tokens.contains("anciano");

        if (!hasNeed && isOnly(tokens, GREETINGS)) {
            return greeting();
        }
        if (!hasNeed && isOnly(tokens, THANKS)) {
            return new Advice("¡De nada! 🐾 Cualquier otra cosa que necesite tu peludo, solo ladrame.", List.of(), defaultSuggestions());
        }

        List<Scored> ranked = rank(tokens, triggers, species, budget, cheap || wantsOffers, wantsOffers, puppy, senior);
        List<Scored> picks = ranked.stream().filter(s -> s.score >= MIN_SCORE).limit(MAX_PRODUCTS).toList();

        if (picks.isEmpty() && !hasNeed && species != Species.NONE) {
            // Solo contó qué mascota tiene: se muestran los productos destacados para ella
            List<Product> featured = featuredFor(species);
            return new Advice("¡Qué lindo " + speciesNoun(species) + "! 🐶 Mientras me contás qué necesita, mirá lo más pedido:",
                    featured.stream().map(p -> p.id).toList(), suggestionsFor(species));
        }

        if (picks.isEmpty()) {
            List<Product> featured = featuredFor(species);
            String budgetNote = budget != null ? " con ese presupuesto" : "";
            return new Advice("Hmm, no encontré nada" + budgetNote + " que encaje con eso 🤔. Contame un poco más (¿es para perro o gato? ¿qué necesita?) "
                    + "o mirá lo más pedido:", featured.stream().map(p -> p.id).toList(), defaultSuggestions());
        }

        return new Advice(buildReply(triggers, species, budget, wantsOffers, picks), picks.stream().map(s -> s.product.id).toList(), followUps(triggers, species));
    }

    // ------------------------------------------------------------------ respuestas fijas

    public Advice intro() {
        return new Advice("¡Guau! Soy Salchi Bot 🌭🎧 Contame qué buscás o cómo es tu mascota y te recomiendo productos de la tienda.",
                List.of(), defaultSuggestions());
    }

    private Advice greeting() {
        return new Advice("¡Hola! 🐾 Soy Salchi Bot, tu asistente de Petshop. Decime qué necesita tu mascota (comida, juguetes, paseo, higiene…) y te armo una recomendación.",
                List.of(), defaultSuggestions());
    }

    private Advice answerFaq(String normalized, List<String> tokens) {
        if (containsAny(tokens, "envio", "enviar", "entrega", "despacho", "llega", "llegan", "demora")) {
            return new Advice("Hacemos envíos a todo el país 🚚 El costo de envío es de $1.500 y lo ves antes de confirmar la compra.", List.of(), defaultSuggestions());
        }
        if (containsAny(tokens, "cuota", "cuotas", "tarjeta", "pago", "pagar", "medio", "efectivo", "financiacion")) {
            return new Advice("Podés pagar con tarjeta y las compras tienen 6 cuotas sin interés 💳 El pago se confirma al finalizar la compra.", List.of(), defaultSuggestions());
        }
        if (containsAny(tokens, "devolucion", "devolver", "cambio", "cambiar", "garantia", "reembolso", "arrepentimiento")) {
            return new Advice("Tenés 30 días de garantía 🛡️ Si algo no salió bien, entrá a \"Mis pedidos\" y pedí la devolución desde ahí.", List.of(), defaultSuggestions());
        }
        if (normalized.contains("quien sos") || normalized.contains("que haces") || normalized.contains("que sos") || containsAny(tokens, "ayuda", "ayudame", "funcionas")) {
            return new Advice("Soy Salchi Bot 🌭🎧, el perrito salchicha de atención al cliente. Leo lo que me escribís y te recomiendo productos de la tienda: probá con \"mi gato se aburre\" o \"algo para el paseo\".",
                    List.of(), defaultSuggestions());
        }
        return null;
    }

    // ------------------------------------------------------------------ puntaje

    private record Scored(Product product, int score) {}

    private List<Scored> rank(List<String> tokens, Set<String> triggers, Species species, Integer budget, boolean preferCheap,
                              boolean wantsOffers, boolean puppy, boolean senior) {
        List<Scored> scored = new ArrayList<>();
        for (Product product : catalog) {
            Set<String> nameStems = new HashSet<>(tokenize(normalize(product.name)));
            Set<String> brandStems = new HashSet<>(tokenize(normalize(product.brand)));
            int score = 0;
            int exactSpeciesTerm = 0;

            for (String token : tokens) {
                if (DOG_WORDS.contains(token) || CAT_WORDS.contains(token)) {
                    // La mascota se maneja aparte (speciesOf). Solo se premia la coincidencia exacta de una palabra
                    // específica: "gatitos" favorece a "Comida húmeda gatitos" por sobre "para gatos".
                    if (!GENERIC_SPECIES.contains(token) && nameStems.contains(token)) {
                        exactSpeciesTerm += 4;
                    }
                    continue;
                }
                if (token.length() < 3 || STOPWORDS.contains(token)) {
                    continue;
                }
                if (nameStems.contains(token)) {
                    score += 5;
                } else if (brandStems.contains(token)) {
                    score += 5;
                } else if (token.length() >= 4 && (nameStems.stream().anyMatch(n -> n.length() >= 4 && (n.startsWith(token) || token.startsWith(n))))) {
                    score += 3;
                }
            }

            for (String trigger : triggers) {
                Map<String, Integer> targets = NEEDS.get(trigger);
                for (Map.Entry<String, Integer> target : targets.entrySet()) {
                    if (nameStems.stream().anyMatch(n -> n.startsWith(target.getKey()))) {
                        score += target.getValue();
                    }
                }
                String category = CATEGORY_HINTS.get(trigger);
                if (category != null && category.equalsIgnoreCase(product.categoryId)) {
                    score += 2;
                }
            }

            if (wantsOffers) {
                score += discountPoints(product);
            }

            // Mascota, edad y presupuesto solo ajustan a los productos que ya encajan con lo que se pidió:
            // sin eso, "mi cachorro tiene que comer" terminaría recomendando pañales.
            if (score > 0) {
                score += exactSpeciesTerm;
                Species productSpecies = speciesOfProduct(nameStems, product.brand);
                if (species != Species.NONE && productSpecies != Species.NONE) {
                    score += species == productSpecies ? 5 : -10;
                }
                if (puppy) {
                    score += nameStems.contains("cachorro") ? 4 : nameStems.contains("senior") ? -10 : 0;
                }
                if (senior) {
                    score += nameStems.contains("senior") ? 4 : nameStems.contains("cachorro") ? -10 : 0;
                }
                if (budget != null) {
                    double price = product.precioPromocional != null ? product.precioPromocional : product.price;
                    if (price > budget) {
                        continue;
                    }
                    score += 1;
                }
                scored.add(new Scored(product, score));
            }
        }

        Comparator<Scored> order = Comparator.comparingInt((Scored s) -> s.score).reversed();
        order = preferCheap
                ? order.thenComparingDouble(s -> s.product.precioPromocional != null ? s.product.precioPromocional : s.product.price)
                : order.thenComparing((Scored s) -> s.product.rating, Comparator.reverseOrder());
        scored.sort(order);
        return scored;
    }

    /** Puntos por descuento: 4 si tiene alguno, más 1 por cada 2,5 % de rebaja (así las mejores ofertas van primero). */
    private static int discountPoints(Product product) {
        double reference = product.precioPromocional != null ? product.price : (product.oldPrice != null ? product.oldPrice : 0);
        double current = product.precioPromocional != null ? product.precioPromocional : product.price;
        if (reference <= 0 || current >= reference) {
            return 0;
        }
        return 4 + (int) ((reference - current) / reference * 40);
    }

    private List<Product> featuredFor(Species species) {
        Set<String> badges = Set.of("Más vendido", "Top", "Popular", "Recomendado");
        return catalog.stream()
                .filter(p -> {
                    Species own = speciesOf(tokenize(normalize(p.name)));
                    return species == Species.NONE || own == Species.NONE || own == species;
                })
                .sorted(Comparator.comparing((Product p) -> p.badge != null && badges.contains(p.badge) ? 0 : 1).thenComparing(p -> p.rating, Comparator.reverseOrder()))
                .limit(MAX_PRODUCTS)
                .toList();
    }

    // ------------------------------------------------------------------ redacción

    private String buildReply(Set<String> triggers, Species species, Integer budget, boolean wantsOffers, List<Scored> picks) {
        String need = triggers.stream().map(NEED_PHRASES::get).filter(p -> p != null).findFirst().orElse(null);
        StringBuilder reply = new StringBuilder("¡Guau! ");
        if (need != null && species != Species.NONE) {
            reply.append("Para ").append(need).append(" de tu ").append(speciesNoun(species)).append(" te recomiendo");
        } else if (need != null) {
            reply.append("Para ").append(need).append(" te recomiendo");
        } else if (species != Species.NONE) {
            reply.append("Para tu ").append(speciesNoun(species)).append(" te recomiendo");
        } else if (wantsOffers) {
            reply.append("Estas ofertas te van a gustar");
        } else {
            reply.append("Esto es lo que encontré");
        }
        if (budget != null) {
            reply.append(" (hasta $").append(String.format(java.util.Locale.US, "%,d", budget).replace(',', '.')).append(")");
        }
        reply.append(":");
        return reply.toString();
    }

    private List<String> followUps(Set<String> triggers, Species species) {
        List<String> out = new ArrayList<>();
        if (species == Species.NONE) {
            out.add("Es para mi perro");
            out.add("Es para mi gato");
        }
        out.add("Algo más barato");
        out.add("¿Qué ofertas hay?");
        return out;
    }

    private List<String> defaultSuggestions() {
        return List.of("Mi perro se aburre", "Algo para el paseo", "Mi gato necesita arena", "¿Qué ofertas hay?");
    }

    private List<String> suggestionsFor(Species species) {
        return species == Species.CAT
                ? List.of("Mi gato se aburre", "Comida para gatitos", "Un rascador", "¿Qué ofertas hay?")
                : List.of("Mi perro se aburre", "Algo para el paseo", "Comida para cachorros", "¿Qué ofertas hay?");
    }

    // ------------------------------------------------------------------ texto

    private enum Species { DOG, CAT, NONE }

    private static String speciesNoun(Species species) {
        return species == Species.CAT ? "gato" : "perro";
    }

    private static Species speciesOf(List<String> tokens) {
        boolean dog = tokens.stream().anyMatch(DOG_WORDS::contains);
        boolean cat = tokens.stream().anyMatch(CAT_WORDS::contains);
        if (dog && !cat) {
            return Species.DOG;
        }
        if (cat && !dog) {
            return Species.CAT;
        }
        return Species.NONE;
    }

    /** Mascota de un producto: por su nombre ("para gatos") o por su marca ("SuperDog", "CatNest", "PuppyCare"). */
    private static Species speciesOfProduct(Set<String> nameStems, String brand) {
        Species byName = speciesOf(new ArrayList<>(nameStems));
        if (byName != Species.NONE) {
            return byName;
        }
        String b = normalize(brand).replace(" ", "");
        boolean dog = b.contains("dog") || b.contains("puppy") || b.contains("paw") && b.contains("dog");
        boolean cat = b.contains("cat") || b.contains("kitt") || b.contains("feline");
        if (dog && !cat) {
            return Species.DOG;
        }
        if (cat && !dog) {
            return Species.CAT;
        }
        return Species.NONE;
    }

    private static Set<String> triggersIn(List<String> tokens) {
        Set<String> found = new LinkedHashSet<>();
        for (String token : tokens) {
            if (NEEDS.containsKey(token)) {
                found.add(token);
                continue;
            }
            for (String trigger : NEEDS.keySet()) {
                if (token.length() >= 5 && trigger.length() >= 5 && (token.startsWith(trigger) || trigger.startsWith(token))) {
                    found.add(trigger);
                    break;
                }
            }
        }
        return found;
    }

    private static Integer budgetOf(String normalized) {
        Matcher matcher = BUDGET.matcher(normalized.replace(".", "").replace(",", ""));
        if (matcher.find()) {
            for (int group = 1; group <= 3; group++) {
                if (matcher.group(group) != null) {
                    return Integer.parseInt(matcher.group(group));
                }
            }
        }
        return null;
    }

    private static boolean isOnly(List<String> tokens, Set<String> allowed) {
        return !tokens.isEmpty() && tokens.stream().allMatch(t -> allowed.contains(t) || STOPWORDS.contains(t));
    }

    private static boolean containsAny(List<String> tokens, String... words) {
        for (String word : words) {
            if (tokens.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Minúsculas, sin tildes ni signos, con espacios simples. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return lower.replaceAll("[^a-z0-9$ ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Palabras sin plural (gatos -> gato, juguetes -> juguete, pañales -> pañal) para que singular y plural coincidan. */
    static List<String> tokenize(String normalized) {
        List<String> tokens = new ArrayList<>();
        for (String raw : normalized.split(" ")) {
            if (raw.isBlank()) {
                continue;
            }
            tokens.add(stem(raw));
        }
        return tokens;
    }

    static String stem(String word) {
        if (word.length() > 4 && word.endsWith("es") && "lrndzs".indexOf(word.charAt(word.length() - 3)) >= 0) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
