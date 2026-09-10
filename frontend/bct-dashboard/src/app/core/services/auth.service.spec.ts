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

  it('should store the JWT and report authenticated on successful login', () => {
    let result: boolean | undefined;
    service.login('admin', 'correct-password').subscribe(ok => (result = ok));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'admin', password: 'correct-password' });
    req.flush({ token: 'fake.jwt.token', username: 'admin', role: 'ADMIN', expiresIn: 3600 });

    expect(result).toBeTrue();
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getToken()).toBe('fake.jwt.token');
  });

  it('should not store a token and report unauthenticated when login fails', () => {
    let result: boolean | undefined;
    service.login('admin', 'wrong-password').subscribe(ok => (result = ok));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBeFalse();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });

  it('should clear the token on logout', () => {
    service.login('admin', 'correct-password').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`)
        .flush({ token: 'fake.jwt.token', username: 'admin', role: 'ADMIN', expiresIn: 3600 });
    expect(service.isAuthenticated()).toBeTrue();

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });
});
