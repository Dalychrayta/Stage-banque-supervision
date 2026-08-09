import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: PLATFORM_ID, useValue: 'browser' }
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('should store the encoded token and report authenticated on successful login', () => {
    let result: boolean | undefined;
    service.login('admin', 'bct2026').subscribe(ok => (result = ok));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/discovery/resources/stats`);
    expect(req.request.headers.get('Authorization')).toBe('Basic ' + btoa('admin:bct2026'));
    req.flush({});

    expect(result).toBeTrue();
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getToken()).toBe(btoa('admin:bct2026'));
  });

  it('should not store a token and report unauthenticated when login fails', () => {
    let result: boolean | undefined;
    service.login('admin', 'wrong-password').subscribe(ok => (result = ok));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/discovery/resources/stats`);
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBeFalse();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });

  it('should clear the token on logout', () => {
    service.login('admin', 'bct2026').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/discovery/resources/stats`).flush({});
    expect(service.isAuthenticated()).toBeTrue();

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });
});
