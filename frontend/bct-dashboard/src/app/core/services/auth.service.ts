import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, map, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

const STORAGE_KEY = 'bct_auth';
const API_BASE = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private isBrowser: boolean;

  constructor(private http: HttpClient, @Inject(PLATFORM_ID) platformId: Object) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  login(username: string, password: string): Observable<boolean> {
    const token = btoa(`${username}:${password}`);
    return this.http.get(`${API_BASE}/discovery/resources/stats`, {
      headers: { Authorization: `Basic ${token}` }
    }).pipe(
      map(() => {
        this.setToken(token);
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

  private setToken(token: string): void {
    if (this.isBrowser) sessionStorage.setItem(STORAGE_KEY, token);
  }
}
