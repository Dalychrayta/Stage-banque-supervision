import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from './auth.service';

function fakeJwtWithRoles(roles: string[]): string {
  const header = btoa(JSON.stringify({ alg: 'none' }));
  const payload = btoa(JSON.stringify({ realm_access: { roles } }));
  return `${header}.${payload}.sig`;
}

describe('AuthService', () => {
  let service: AuthService;
  let oauth: jasmine.SpyObj<OAuthService>;

  beforeEach(() => {
    oauth = jasmine.createSpyObj('OAuthService', [
      'configure', 'setupAutomaticSilentRefresh', 'loadDiscoveryDocumentAndTryLogin',
      'initLoginFlow', 'logOut', 'hasValidAccessToken', 'getAccessToken', 'getIdentityClaims'
    ]);
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: OAuthService, useValue: oauth },
        { provide: PLATFORM_ID, useValue: 'browser' }
      ]
    });
    service = TestBed.inject(AuthService);
  });

  it('reports authenticated based on the OAuth token', () => {
    oauth.hasValidAccessToken.and.returnValue(true);
    expect(service.isAuthenticated()).toBeTrue();
    oauth.hasValidAccessToken.and.returnValue(false);
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('extracts realm roles from the access token', () => {
    oauth.getAccessToken.and.returnValue(fakeJwtWithRoles(['OPERATOR', 'offline_access']));
    expect(service.getRoles()).toEqual(['OPERATOR']);
    expect(service.hasRole('OPERATOR')).toBeTrue();
    expect(service.hasRole('ADMIN')).toBeFalse();
  });

  it('canOperate is true for OPERATOR or ADMIN, false for VIEWER', () => {
    oauth.getAccessToken.and.returnValue(fakeJwtWithRoles(['VIEWER']));
    expect(service.canOperate()).toBeFalse();
    oauth.getAccessToken.and.returnValue(fakeJwtWithRoles(['ADMIN']));
    expect(service.canOperate()).toBeTrue();
  });

  it('login() delegates to the Keycloak redirect flow', () => {
    service.login();
    expect(oauth.initLoginFlow).toHaveBeenCalled();
  });

  it('returns the preferred_username from identity claims', () => {
    oauth.getIdentityClaims.and.returnValue({ preferred_username: 'operator.bct' });
    expect(service.getUsername()).toBe('operator.bct');
  });
});
