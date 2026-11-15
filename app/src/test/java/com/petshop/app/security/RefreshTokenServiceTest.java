package com.petshop.app.security;

import com.petshop.app.model.RefreshToken;
import com.petshop.app.repository.RefreshTokenRepository;
import com.petshop.app.service.RefreshTokenService;
import com.petshop.app.service.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final Map<String, RefreshToken> db = new HashMap<>();
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            db.put(token.tokenHash, token);
            return token;
        });
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<String>getArgument(0))));
        when(repository.findByUserId(anyString())).thenAnswer(inv ->
                db.values().stream().filter(t -> t.userId.equals(inv.getArgument(0))).toList());
        when(repository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<RefreshToken> saved = new ArrayList<>();
            for (RefreshToken token : inv.<Iterable<RefreshToken>>getArgument(0)) {
                db.put(token.tokenHash, token);
                saved.add(token);
            }
            return saved;
        });
        service = new RefreshTokenService(repository, 60_000);
    }

    @Test
    void onlyTheHashIsStoredNeverTheTokenItself() {
        String raw = service.issue("user-1");

        assertThat(db).containsKey(TokenHasher.hash(raw)).doesNotContainKey(raw);
    }

    @Test
    void rotatingConsumesTheOldTokenAndReturnsANewOne() {
        String raw = service.issue("user-1");

        var rotation = service.rotate(raw);

        assertThat(rotation).isPresent();
        assertThat(rotation.get().userId()).isEqualTo("user-1");
        assertThat(rotation.get().refreshToken()).isNotEqualTo(raw);
        assertThat(service.rotate(rotation.get().refreshToken())).isPresent();
    }

    @Test
    void reusingAnAlreadyUsedTokenRevokesEveryTokenOfTheUser() {
        String stolen = service.issue("user-1");
        String latest = service.rotate(stolen).orElseThrow().refreshToken();

        assertThat(service.rotate(stolen)).isEmpty();
        assertThat(service.rotate(latest)).isEmpty();
    }

    @Test
    void unknownBlankAndExpiredTokensAreRejected() {
        RefreshTokenService shortLived = new RefreshTokenService(mock(RefreshTokenRepository.class), -1);

        assertThat(service.rotate("desconocido")).isEmpty();
        assertThat(service.rotate(null)).isEmpty();
        assertThat(service.rotate("  ")).isEmpty();
        assertThat(shortLived.rotate("x")).isEmpty();

        String raw = service.issue("user-2");
        db.get(TokenHasher.hash(raw)).expiryEpochMillis = System.currentTimeMillis() - 1;
        assertThat(service.rotate(raw)).isEmpty();
    }

    @Test
    void revokeAllClosesEverySessionOfTheUserOnly() {
        String a = service.issue("user-1");
        String b = service.issue("user-1");
        String other = service.issue("user-2");

        service.revokeAll("user-1");

        assertThat(service.rotate(a)).isEmpty();
        assertThat(service.rotate(b)).isEmpty();
        assertThat(service.rotate(other)).isPresent();
    }

    @Test
    void logoutRevokesTheToken() {
        String raw = service.issue("user-1");

        service.revoke(raw);

        assertThat(service.rotate(raw)).isEmpty();
    }
}
