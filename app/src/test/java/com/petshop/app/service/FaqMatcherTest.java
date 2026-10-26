package com.petshop.app.service;

import com.petshop.app.service.FaqMatcher.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FaqMatcherTest {

    private final FaqMatcher matcher = new FaqMatcher(FaqDefaults.items().stream().map(i -> new Entry(i.question, i.answer)).toList());

    private String answerTo(String message) {
        FaqMatcher.Match match = matcher.best(message);
        return match == null ? null : match.entry().question();
    }

    @Test
    void matchesTheTypicalQuestionsEvenWithDifferentWords() {
        assertThat(answerTo("cuánto tarda en llegar")).isEqualTo("¿Cuánto tarda en llegar mi pedido?");
        assertThat(answerTo("cuánto cuesta el envío?")).isEqualTo("¿Cuánto cuesta el envío? ¿Hay envío gratis?");
        assertThat(answerTo("quiero devolver algo")).isEqualTo("¿Cómo devuelvo un producto?");
        assertThat(answerTo("hacen devoluciones?")).isEqualTo("¿Cómo devuelvo un producto?");
        assertThat(answerTo("¿en cuántas cuotas puedo pagar?")).isEqualTo("¿Qué medios de pago aceptan? ¿Hay cuotas?");
        assertThat(answerTo("me olvidé la clave")).isEqualTo("Olvidé mi contraseña, ¿cómo la recupero?");
        assertThat(answerTo("cuál es el horario de atención")).isEqualTo("¿Cómo los contacto? ¿Cuál es el horario de atención?");
        assertThat(answerTo("puedo comprar sin cuenta")).isEqualTo("¿Necesito una cuenta para comprar?");
        assertThat(answerTo("cómo uso un cupón")).isEqualTo("¿Cómo uso un cupón de descuento?");
        assertThat(answerTo("qué es la ruleta")).isEqualTo("¿Qué es el descuento del día (la ruleta)?");
    }

    @Test
    void doesNotMatchWhenTheMessageHasNothingToDoWithTheFaq() {
        assertThat(matcher.best("mi perro se aburre")).isNull();
        assertThat(matcher.best("algo para el paseo")).isNull();
        assertThat(matcher.best("hola")).isNull();
        assertThat(matcher.best("")).isNull();
    }

    @Test
    void anEmptyFaqNeverMatches() {
        assertThat(new FaqMatcher(List.of()).best("cuánto tarda el envío")).isNull();
        assertThat(new FaqMatcher(null).isEmpty()).isTrue();
    }

    @Test
    void worksWithWhateverTheAdminWrites() {
        FaqMatcher custom = new FaqMatcher(List.of(
                new Entry("¿Tienen local a la calle?", "Sí, en Palermo."),
                new Entry("¿Hacen regalos corporativos?", "Escribinos para armar un pedido especial.")));

        assertThat(custom.best("tienen local?").entry().answer()).isEqualTo("Sí, en Palermo.");
        assertThat(custom.best("regalos para empresas").entry().answer()).startsWith("Escribinos");
    }

    @Test
    void recognizesARequestForTheWholeList() {
        assertThat(matcher.asksForTheList("preguntas frecuentes")).isTrue();
        assertThat(matcher.asksForTheList("faq")).isTrue();
        assertThat(matcher.asksForTheList("mi perro se aburre")).isFalse();
    }
}
