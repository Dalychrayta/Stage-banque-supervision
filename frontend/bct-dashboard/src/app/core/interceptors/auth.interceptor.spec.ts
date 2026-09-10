import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken', 'login']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('attaches the Bearer token when present', () => {
    authServiceSpy.getToken.and.returnValue('kc-access-token');
    httpClient.get('/api/anything').subscribe();
    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.get('Authorization')).toBe('Bearer kc-access-token');
    req.flush({});
  });

  it('sends no Authorization header when there is no token', () => {
    authServiceSpy.getToken.and.returnValue(null);
    httpClient.get('/api/anything').subscribe();
    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('triggers Keycloak login on a 401 response', () => {
    authServiceSpy.getToken.and.returnValue('expired');
    httpClient.get('/api/anything').subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/anything');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    expect(authServiceSpy.login).toHaveBeenCalled();
  });

  it('does not trigger login on a non-401 error', () => {
    authServiceSpy.getToken.and.returnValue('t');
    httpClient.get('/api/anything').subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/anything');
    req.flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });
});
