package com.petshop.app.controller;

import com.petshop.app.dto.ChatRequest;
import com.petshop.app.dto.ChatResponse;
import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.repository.FaqItemRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.service.FaqDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private ProductVariantRepository variantRepository;
    private ChatController controller;
    private FaqItemRepository faqRepository;

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        when(productRepository.findAll()).thenReturn(List.of(
                new Product("p33", "Correa reflectante 1.5m", "NightWalk", 1299, 1699.0, 4.5, "/products/p33.svg", "Nuevo", "accesorios", 118),
                new Product("p13", "Arnés de paseo pequeño", "TrailFit", 1599, 1899.0, 4.5, "/products/p13.svg", "Popular", "accesorios", 130)));
        when(variantRepository.findByProductIdIn(anyList())).thenReturn(List.of());
        faqRepository = mock(FaqItemRepository.class);
        when(faqRepository.findAllByOrderByPositionAscIdAsc()).thenReturn(FaqDefaults.items());
        controller = new ChatController(productRepository, variantRepository, faqRepository);
    }

    private ChatRequest request(String message, List<String> history) {
        ChatRequest request = new ChatRequest();
        request.message = message;
        request.history = history;
        return request;
    }

    @Test
    void recommendsRealCatalogProductsForAFreeTextMessage() {
        ResponseEntity<?> response = controller.chat(request("quiero algo para pasear a mi perro", List.of()));

        ChatResponse body = (ChatResponse) response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.reply).startsWith("¡Guau!");
        assertThat(body.products).extracting(p -> p.id).contains("p33", "p13");
        assertThat(body.suggestions).isNotEmpty();
    }

    @Test
    void answersAFrequentQuestionFromTheDatabaseInsteadOfRecommendingProducts() {
        ChatResponse body = (ChatResponse) controller.chat(request("¿cómo hago una devolución?", List.of())).getBody();

        assertThat(body.reply).contains("Mis pedidos");
        assertThat(body.products).isEmpty();
        assertThat(body.suggestions).isNotEmpty();
    }

    @Test
    void usesTheAnswerTheAdminWrote() {
        when(faqRepository.findAllByOrderByPositionAscIdAsc()).thenReturn(List.of(
                new com.petshop.app.model.FaqItem("¿Tienen local a la calle?", "Sí, estamos en Palermo, Buenos Aires.", 0)));

        ChatResponse body = (ChatResponse) controller.chat(request("tienen local?", List.of())).getBody();

        assertThat(body.reply).isEqualTo("Sí, estamos en Palermo, Buenos Aires.");
    }

    @Test
    void marksProductsThatHaveVariantsSoTheCustomerPicksOneFirst() {
        ProductVariant variant = new ProductVariant();
        variant.productId = "p13";
        when(variantRepository.findByProductIdIn(anyList())).thenReturn(List.of(variant));

        ChatResponse body = (ChatResponse) controller.chat(request("un arnés", List.of())).getBody();

        assertThat(body.products).filteredOn(p -> p.id.equals("p13")).allMatch(p -> p.hasVariants);
        assertThat(body.products).filteredOn(p -> p.id.equals("p33")).allMatch(p -> !p.hasVariants);
    }

    @Test
    void aMissingBodyIsABadRequestAndAnEmptyMessageGreets() {
        assertThat(controller.chat(null).getStatusCode().value()).isEqualTo(400);

        ChatResponse greeting = (ChatResponse) controller.chat(request("   ", null)).getBody();
        assertThat(greeting.reply).contains("Salchi Bot");
        assertThat(greeting.products).isEmpty();
    }

    @Test
    void onlyTheLastMessagesOfTheHistoryAreUsed() {
        List<String> history = List.of("a", "b", "c", "d", "tengo un gato", "otro");

        ResponseEntity<?> response = controller.chat(request("hola", history));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
