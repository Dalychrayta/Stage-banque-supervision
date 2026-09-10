import { TestBed } from '@angular/core/testing';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'login']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceSpy }]
    });
  });

  function runGuard(): boolean {
    return TestBed.runInInjectionContext(() => authGuard({} as any, {} as any)) as boolean;
  }

  it('allows navigation when authenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    expect(runGuard()).toBeTrue();
    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });

  it('blocks navigation and triggers Keycloak login when not authenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    expect(runGuard()).toBeFalse();
    expect(authServiceSpy.login).toHaveBeenCalled();
  });
});
