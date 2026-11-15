package com.petshop.app.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Busca, entre las preguntas frecuentes que edita el administrador, la que mejor responde a lo que escribió el cliente.
 *
 * Compara "conceptos" y no palabras exactas: devolver / devuelvo / devolución, o envío / entrega / llega, cuentan como
 * lo mismo. Una coincidencia con la pregunta vale más que con la respuesta. Es determinista y no usa servicios externos.
 */
public class FaqMatcher {

    /** Una pregunta con su respuesta. */
    public record Entry(String question, String answer) {}

    /** La pregunta elegida y qué tan bien encaja (3 puntos por cada concepto de la pregunta que el cliente nombró). */
    public record Match(Entry entry, int index, int score) {}

    /** Puntaje mínimo para considerar que la pregunta responde lo que se escribió (un concepto en común con la pregunta). */
    public static final int MIN_SCORE = 3;
    /** Puntaje a partir del cual la coincidencia es tan clara que se prefiere a recomendar productos. */
    public static final int STRONG_SCORE = 6;

    /** Palabras que no ayudan a distinguir una pregunta de otra (ni de las mascotas, para que "mi perro" no active nada). */
    private static final Set<String> IGNORED = stems(
            "de la el los las un una unos unas para por con que quiero necesito saber quisiera queria puedo podes pueden hacer hago hacen hay tienen tenes "
                    + "tiene tengo mi mis tu tus su sus si no se me te lo le y o a en al del ya muy mas menos como cual cuales cuando donde cuanto cuesta "
                    + "cuestan precio valor es son ser esta estan estoy hola buenas gracias favor tienda petshop producto productos tipo cosa algo "
                    + "perro perrito perra cachorro gato gatito gata gatita mascota");

    /** Grupos de palabras que significan lo mismo para una pregunta frecuente. La primera es el nombre del concepto. */
    private static final String[][] GROUPS = {
            {"envio", "enviar", "enviamos", "envian", "enviado", "entrega", "entregar", "entregan", "despacho", "despachar", "llega", "llegar", "llegan",
                    "demora", "demorar", "tarda", "tardar", "tardan", "plazo", "domicilio", "correo"},
            {"pago", "pagar", "pagan", "cuota", "tarjeta", "credito", "debito", "efectivo", "transferencia", "financiacion", "medio", "abonar", "abono"},
            {"devolucion", "devolver", "devuelvo", "devuelve", "devolvemos", "devuelvan", "reembolso", "reintegro", "arrepentimiento"},
            {"cambio", "cambiar", "cambian", "cambiamos"},
            {"garantia", "falla", "fallo", "defecto", "defectuoso", "roto", "rota", "fallado"},
            {"cuenta", "registro", "registrar", "registrarme", "usuario", "invitado", "sesion", "ingresar", "login"},
            {"contrasena", "clave", "password", "olvide", "olvido", "recuperar"},
            {"contacto", "contactar", "telefono", "mail", "email", "horario", "atencion", "whatsapp", "comunicar", "comunico", "escribir", "soporte", "hablar"},
            {"cupon", "codigo"},
            {"pedido", "orden", "compra", "seguimiento", "rastrear", "rastreo", "seguir", "estado", "tracking"},
            {"stock", "disponible", "disponibilidad", "agotado", "agotar", "reponen", "reposicion"},
            {"ruleta", "sorteo", "girar", "gira"},
            {"preguntas", "pregunta", "faq", "frecuente"},
    };

    private static final Map<String, String> CONCEPTS = new HashMap<>();

    static {
        for (String[] group : GROUPS) {
            String concept = ChatAdvisor.stem(ChatAdvisor.normalize(group[0]));
            for (String word : group) {
                CONCEPTS.put(ChatAdvisor.stem(ChatAdvisor.normalize(word)), concept);
            }
        }
    }

    private final List<Entry> entries;
    private final List<Set<String>> questionConcepts;
    private final List<Set<String>> answerConcepts;
    private final List<Set<String>> questionWords;

    public FaqMatcher(List<Entry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.questionConcepts = this.entries.stream().map(e -> conceptsOf(e.question())).toList();
        this.answerConcepts = this.entries.stream().map(e -> conceptsOf(e.answer())).toList();
        this.questionWords = this.entries.stream().map(e -> wordsOf(e.question())).toList();
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** ¿El cliente está pidiendo ver las preguntas frecuentes en general? */
    public boolean asksForTheList(String message) {
        Set<String> concepts = conceptsOf(message);
        return concepts.contains(CONCEPTS.get(ChatAdvisor.stem("preguntas"))) || concepts.contains(CONCEPTS.get(ChatAdvisor.stem("faq")));
    }

    /** La pregunta que mejor responde al mensaje, o null si ninguna comparte un concepto con él. */
    public Match best(String message) {
        Set<String> wanted = conceptsOf(message);
        if (wanted.isEmpty()) {
            return null;
        }
        Set<String> words = wordsOf(message);
        Match best = null;
        int bestOverlap = -1;
        double bestCoverage = -1;
        for (int i = 0; i < entries.size(); i++) {
            Set<String> question = questionConcepts.get(i);
            int questionHits = (int) wanted.stream().filter(question::contains).count();
            if (questionHits == 0) {
                continue;
            }
            Set<String> answer = answerConcepts.get(i);
            int answerHits = (int) wanted.stream().filter(c -> !question.contains(c) && answer.contains(c)).count();
            int score = questionHits * 3 + answerHits;
            // Desempate: más palabras exactas en común (incluso las comunes, como "cuesta") y luego mayor parte de la pregunta cubierta
            int overlap = (int) words.stream().filter(questionWords.get(i)::contains).count();
            double coverage = (double) questionHits / question.size();
            boolean better = best == null || score > best.score()
                    || (score == best.score() && (overlap > bestOverlap || (overlap == bestOverlap && coverage > bestCoverage)));
            if (better) {
                best = new Match(entries.get(i), i, score);
                bestOverlap = overlap;
                bestCoverage = coverage;
            }
        }
        return best != null && best.score() >= MIN_SCORE ? best : null;
    }

    /** Conceptos del texto: las palabras agrupadas por significado, sin las que no distinguen nada. */
    static Set<String> conceptsOf(String text) {
        Set<String> concepts = new HashSet<>();
        for (String token : ChatAdvisor.tokenize(ChatAdvisor.normalize(text))) {
            if (token.length() < 3 || IGNORED.contains(token)) {
                continue;
            }
            String concept = CONCEPTS.get(token);
            concepts.add(concept != null ? concept : token.length() > 6 ? token.substring(0, 6) : token);
        }
        return concepts;
    }

    /** Todas las palabras del texto (sin plural), también las comunes: sirven para desempatar. */
    private static Set<String> wordsOf(String text) {
        Set<String> words = new HashSet<>();
        for (String token : ChatAdvisor.tokenize(ChatAdvisor.normalize(text))) {
            if (token.length() >= 3) {
                words.add(token);
            }
        }
        return words;
    }

    private static Set<String> stems(String words) {
        Set<String> set = new HashSet<>();
        for (String word : words.split(" ")) {
            set.add(ChatAdvisor.stem(ChatAdvisor.normalize(word)));
        }
        return set;
    }
}
