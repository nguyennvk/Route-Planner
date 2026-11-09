import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { LoginResponse } from './models';
import { Session } from './session';

const tokens = (access: string, refresh: string): LoginResponse =>
  ({ accessToken: access, refreshToken: refresh, tokenType: 'Bearer', expiresIn: 900 });

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: Session;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(Session);
    session.store(tokens('old-access', 'old-refresh'));
  });

  it('adds the bearer token, except on the public auth endpoints', () => {
    http.get('/api/v1/companies').subscribe();
    expect(backend.expectOne('/api/v1/companies').request.headers.get('Authorization')).toBe('Bearer old-access');

    http.post('/api/v1/auth/login', {}).subscribe();
    expect(backend.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);

    http.get('/api/v1/shared/routes/abc').subscribe();
    expect(backend.expectOne('/api/v1/shared/routes/abc').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on a 401 and replays the request with the new token', () => {
    let body: unknown;
    http.get('/api/v1/companies').subscribe((res) => (body = res));

    backend.expectOne('/api/v1/companies').flush(null, { status: 401, statusText: 'Unauthorized' });
    const refresh = backend.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'old-refresh' });
    refresh.flush(tokens('new-access', 'new-refresh'));

    const retry = backend.expectOne('/api/v1/companies');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
    retry.flush(['ok']);
    expect(body).toEqual(['ok']);
    expect(session.refreshToken()).toBe('new-refresh');
  });

  it('shares one refresh between parallel 401s, because refresh tokens rotate', () => {
    http.get('/api/v1/a').subscribe();
    http.get('/api/v1/b').subscribe();
    backend.expectOne('/api/v1/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectOne('/api/v1/auth/refresh').flush(tokens('new-access', 'new-refresh'));

    expect(backend.expectOne('/api/v1/a').request.headers.get('Authorization')).toBe('Bearer new-access');
    expect(backend.expectOne('/api/v1/b').request.headers.get('Authorization')).toBe('Bearer new-access');
    backend.verify();
  });

  it('logs out when the refresh token is rejected', () => {
    let failed = false;
    http.get('/api/v1/companies').subscribe({ error: () => (failed = true) });
    backend.expectOne('/api/v1/companies').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(session.loggedIn()).toBe(false);
  });

  it('leaves other errors and the session alone', () => {
    let status = 0;
    http.get('/api/v1/companies').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/api/v1/companies').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
    expect(session.loggedIn()).toBe(true);
    backend.verify();
  });
});
