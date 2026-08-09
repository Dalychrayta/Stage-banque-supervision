import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken', 'logout']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should attach the Authorization header when a token is present', () => {
    authServiceSpy.getToken.and.returnValue('dGVzdDp0ZXN0');

    httpClient.get('/api/anything').subscribe();

    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.get('Authorization')).toBe('Basic dGVzdDp0ZXN0');
    req.flush({});
  });

  it('should not attach an Authorization header when no token is present', () => {
    authServiceSpy.getToken.and.returnValue(null);

    httpClient.get('/api/anything').subscribe();

    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should log out and redirect to /login on a 401 response', () => {
    authServiceSpy.getToken.and.returnValue('dGVzdDp0ZXN0');

    httpClient.get('/api/anything').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/anything');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should not log out on a non-401 error response', () => {
    authServiceSpy.getToken.and.returnValue('dGVzdDp0ZXN0');

    httpClient.get('/api/anything').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/anything');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});
