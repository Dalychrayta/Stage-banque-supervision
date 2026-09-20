package com.bct.rca.controller;

import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.service.RcaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une requête fautive du client (identifiant inconnu, catégorie vide) doit
 * recevoir un 4xx explicite, jamais un 500 : un 500 signale une panne du
 * serveur et remplit les logs de traces pour rien.
 */
class RcaControllerTest {

    private RcaService rcaService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rcaService = mock(RcaService.class);
        // Fournit le jeton attendu par @AuthenticationPrincipal sans démarrer Spring Security.
        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter p) {
                return p.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
                                          NativeWebRequest r, WebDataBinderFactory b) {
                return Jwt.withTokenValue("t").header("alg", "none").claim("preferred_username", "operator").build();
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(new RcaController(rcaService))
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void resolvingAnUnknownIncidentIsA404() throws Exception {
        when(rcaService.resolve(999999999L)).thenThrow(new NoSuchElementException("Incident non trouvé: 999999999"));

        mvc.perform(patch("/api/rca/999999999/resolve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Incident non trouvé: 999999999"));
    }

    @Test
    void correctingTheCategoryOfAnUnknownIncidentIsA404() throws Exception {
        when(rcaService.correctCategory(eq(999999999L), any(), any()))
                .thenThrow(new NoSuchElementException("Incident non trouvé: 999999999"));

        mvc.perform(patch("/api/rca/999999999/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Incident non trouvé: 999999999"));
    }

    @Test
    void anEmptyCorrectionCategoryIsA400() throws Exception {
        when(rcaService.correctCategory(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Catégorie de correction vide"));

        mvc.perform(patch("/api/rca/1/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Catégorie de correction vide"));
    }

    @Test
    void resolvingAKnownIncidentStillWorks() throws Exception {
        when(rcaService.resolve(1L)).thenReturn(new IncidentAnalysis());

        mvc.perform(patch("/api/rca/1/resolve")).andExpect(status().isOk());
    }
}
