import { AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';

/** Configuration OpenID Connect vers Keycloak (realm "bct").
 *  Flux "code" + PKCE : le navigateur est redirigé vers l'écran de
 *  connexion de Keycloak, puis revient avec un jeton.
 *  Construite à l'appel (pas au chargement du module) car elle lit
 *  window.location — indisponible côté serveur (SSR / prerender). */
export function buildAuthConfig(): AuthConfig {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:4200';
  return {
    issuer: environment.keycloak.issuer,
    redirectUri: origin,
    postLogoutRedirectUri: origin,
    clientId: environment.keycloak.clientId,
    responseType: 'code',
    scope: 'openid profile roles',
    showDebugInformation: false,
    requireHttps: false // dev uniquement (Keycloak en HTTP local)
  };
}
