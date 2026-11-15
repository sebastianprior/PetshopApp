package com.petshop.app.service;

import com.petshop.app.model.FaqItem;
import com.petshop.app.model.ShippingZone;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.FaqItemRepository;
import com.petshop.app.repository.ShippingZoneRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los datos de ejemplo se cargan la primera vez y nunca vuelven si el administrador los borra a propósito. */
class ShippingAndFaqSeedersTest {

    private SiteSettingsRepository settings;
    private ShippingZoneRepository zones;
    private FaqItemRepository faq;

    @BeforeEach
    void setUp() {
        settings = mock(SiteSettingsRepository.class);
        zones = mock(ShippingZoneRepository.class);
        faq = mock(FaqItemRepository.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shippingSeederLoadsTheTwoZonesAndTheDefaultsTheFirstTime() {
        new ShippingZoneSeeder(zones, settings).run(null);

        ArgumentCaptor<List<ShippingZone>> saved = ArgumentCaptor.forClass(List.class);
        verify(zones).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        ShippingZone caba = saved.getValue().get(0);
        assertThat(caba.name).isEqualTo("CABA");
        assertThat(caba.postalFrom).isEqualTo(1000);
        assertThat(caba.postalTo).isEqualTo(1499);
        assertThat(caba.cost).isEqualTo(1200.0);
        ShippingZone gba = saved.getValue().get(1);
        assertThat(gba.postalFrom).isEqualTo(1500);
        assertThat(gba.cost).isEqualTo(1500.0);

        ArgumentCaptor<SiteSettings> savedSettings = ArgumentCaptor.forClass(SiteSettings.class);
        verify(settings).save(savedSettings.capture());
        assertThat(savedSettings.getValue().shippingDefaultCost).isEqualTo(2500.0);
        assertThat(savedSettings.getValue().shippingDefaultEta).isEqualTo("4 a 7 días hábiles");
        assertThat(savedSettings.getValue().shippingSeeded).isTrue();
    }

    @Test
    void shippingSeederDoesNothingOnceItHasRun() {
        SiteSettings done = new SiteSettings();
        done.shippingSeeded = true;
        when(settings.findById(SiteSettings.SINGLETON_ID)).thenReturn(Optional.of(done));

        new ShippingZoneSeeder(zones, settings).run(null);

        verifyNoInteractions(zones);
        verify(settings, never()).save(any());
    }

    @Test
    void shippingSeederDoesNotAddZonesNextToTheOnesTheAdminAlreadyHas() {
        when(zones.count()).thenReturn(1L);
        SiteSettings custom = new SiteSettings();
        custom.shippingDefaultCost = 3100.0;
        custom.shippingDefaultEta = "10 días";
        when(settings.findById(SiteSettings.SINGLETON_ID)).thenReturn(Optional.of(custom));

        new ShippingZoneSeeder(zones, settings).run(null);

        verify(zones, never()).saveAll(any());
        assertThat(custom.shippingDefaultCost).isEqualTo(3100.0);
        assertThat(custom.shippingDefaultEta).isEqualTo("10 días");
        assertThat(custom.shippingSeeded).isTrue();
        verify(settings).save(custom);
    }

    @Test
    void faqSeederLoadsTheDefaultQuestionsTheFirstTime() {
        new FaqSeeder(faq, settings).run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FaqItem>> saved = ArgumentCaptor.forClass(List.class);
        verify(faq).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty();
        assertThat(saved.getValue()).allSatisfy(item -> {
            assertThat(item.question).isNotBlank();
            assertThat(item.answer).isNotBlank();
        });
        ArgumentCaptor<SiteSettings> savedSettings = ArgumentCaptor.forClass(SiteSettings.class);
        verify(settings).save(savedSettings.capture());
        assertThat(savedSettings.getValue().faqSeeded).isTrue();
    }

    @Test
    void faqSeederDoesNotBringBackQuestionsTheAdminDeleted() {
        SiteSettings done = new SiteSettings();
        done.faqSeeded = true;
        when(settings.findById(SiteSettings.SINGLETON_ID)).thenReturn(Optional.of(done));
        when(faq.count()).thenReturn(0L);

        new FaqSeeder(faq, settings).run(null);

        verifyNoInteractions(faq);
        verify(settings, never()).save(any());
    }

    @Test
    void faqSeederKeepsTheQuestionsThatAlreadyExist() {
        when(faq.count()).thenReturn(5L);

        new FaqSeeder(faq, settings).run(null);

        verify(faq, never()).saveAll(any());
        ArgumentCaptor<SiteSettings> savedSettings = ArgumentCaptor.forClass(SiteSettings.class);
        verify(settings).save(savedSettings.capture());
        assertThat(savedSettings.getValue().faqSeeded).isTrue();
    }
}
