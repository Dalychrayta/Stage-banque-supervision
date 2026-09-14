package com.bct.healing.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

/**
 * Fournit un jeton d'identité MACHINE (pas un jeton d'utilisateur humain) pour
 * les appels service-à-service qui ont besoin d'être authentifiés.
 *
 * Pourquoi ce composant existe : rca-service vérifie désormais ses propres
 * jetons (endpoints d'écriture protégés, port 8083 directement joignable).
 * Or auto-healing-service appelle PATCH /api/rca/{id}/resolve tout seul, sans
 * utilisateur derrière — il n'y a pas de jeton humain à transmettre. Un compte
 * de service Keycloak dédié ("bct-auto-healing", grant client_credentials,
 * rôle OPERATOR) donne à ce service sa propre identité vérifiable, plutôt que
 * de rouvrir l'endpoint sans authentification.
 */
@Component
@Slf4j
public class KeycloakServiceTokenProvider {

    @Value("${keycloak.token-uri:http://localhost:8180/realms/bct/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${keycloak.client-id:bct-auto-healing}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    private final WebClient webClient = WebClient.create();

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    /**
     * Jeton valide, réutilisé tant qu'il ne va pas expirer dans moins de 30 s.
     * synchronized : évite que deux appels concurrents ne redemandent chacun
     * un jeton au même instant.
     */
    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<?, ?> response = webClient.post().uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        cachedToken = (String) response.get("access_token");
        int expiresIn = ((Number) response.get("expires_in")).intValue();
        // Marge de 30 s : on renouvelle un peu avant l'expiration réelle,
        // jamais pile au moment où une requête serait déjà en vol.
        expiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 30, 5));
        log.debug("Nouveau jeton de service obtenu (valide {}s)", expiresIn);
        return cachedToken;
    }
}
