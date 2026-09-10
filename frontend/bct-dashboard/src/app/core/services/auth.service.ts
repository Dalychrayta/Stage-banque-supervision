import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { OAuthService } from 'angular-oauth2-oidc';
import { buildAuthConfig } from '../auth/auth.config';

export type Role = 'VIEWER' | 'OPERATOR' | 'ADMIN';

/**
 * Authentification déléguée à Keycloak (OpenID Connect).
 * L'API Gateway ne fait que vérifier les jetons émis ici.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly isBrowser: boolean;

  constructor(
    private oauth: OAuthService,
    @Inject(PLATFORM_ID) platformId: Object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  /** Appelé une fois au démarrage de l'app (voir app.config.ts).
   *  Ne fait rien côté serveur (SSR / prerender). */
  async init(): Promise<void> {
    if (!this.isBrowser) return;
    this.oauth.configure(buildAuthConfig());
    this.oauth.setupAutomaticSilentRefresh();
    await this.oauth.loadDiscoveryDocumentAndTryLogin();
  }

  /** Redirige vers l'écran de connexion Keycloak. */
  login(): void {
    if (this.isBrowser) this.oauth.initLoginFlow();
  }

  logout(): void {
    if (this.isBrowser) this.oauth.logOut();
  }

  isAuthenticated(): boolean {
    return this.isBrowser && this.oauth.hasValidAccessToken();
  }

  getToken(): string | null {
    return this.isBrowser ? (this.oauth.getAccessToken() || null) : null;
  }

  getUsername(): string | null {
    if (!this.isBrowser) return null;
    const claims = this.oauth.getIdentityClaims() as Record<string, unknown> | null;
    return (claims?.['preferred_username'] as string) ?? null;
  }

  /** Rôles portés par le jeton (realm_access.roles), filtrés sur les 3 connus. */
  getRoles(): Role[] {
    const token = this.getToken();
    if (!token) return [];
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const roles: string[] = payload?.realm_access?.roles ?? [];
      return roles.filter(r => r === 'VIEWER' || r === 'OPERATOR' || r === 'ADMIN') as Role[];
    } catch {
      return [];
    }
  }

  hasRole(role: Role): boolean {
    return this.getRoles().includes(role);
  }

  /** Peut déclencher une action (réparation, résolution, correction). */
  canOperate(): boolean {
    return this.hasRole('OPERATOR') || this.hasRole('ADMIN');
  }
}
