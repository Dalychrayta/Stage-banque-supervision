import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, map, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

const STORAGE_KEY = 'bct_auth_token';
const API_BASE = environment.apiBaseUrl;

interface LoginResponse {
  token: string;
  username: string;
  role: string;
  expiresIn: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private isBrowser: boolean;

  constructor(private http: HttpClient, @Inject(PLATFORM_ID) platformId: Object) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  login(username: string, password: string): Observable<boolean> {
    return this.http.post<LoginResponse>(`${API_BASE}/auth/login`, { username, password }).pipe(
      map(response => {
        this.setToken(response.token);
        return true;
      }),
      catchError(() => of(false))
    );
  }

  logout(): void {
    if (this.isBrowser) sessionStorage.removeItem(STORAGE_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getToken(): string | null {
    return this.isBrowser ? sessionStorage.getItem(STORAGE_KEY) : null;
  }

  /** Nom d'utilisateur porté par le jeton JWT (claim "sub"), ou null. */
  getUsername(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub ?? null;
    } catch {
      return null;
    }
  }

  private setToken(token: string): void {
    if (this.isBrowser) sessionStorage.setItem(STORAGE_KEY, token);
  }
}
