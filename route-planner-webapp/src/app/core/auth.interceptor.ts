import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { Api } from './api';
import { LoginResponse } from './models';
import { Session } from './session';

// These answer 401 for their own reasons (wrong password, bad refresh token), so they must not trigger a refresh
const PUBLIC_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];

// Refresh tokens rotate: using one twice fails. Parallel requests that all get a 401 therefore
// have to share a single refresh call instead of each starting their own.
let refreshInFlight: Observable<LoginResponse> | null = null;

// Plays the role of a servlet filter, but on the way out of the client instead of into the server
// Share links work without a login. Sending an expired token there would make Spring answer 401 anyway
// (a bad bearer token is rejected before permitAll is checked), and the refresh path below would end on /login
const SHARED_PREFIX = '/api/v1/shared/';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (PUBLIC_PATHS.some((path) => req.url.endsWith(path)) || req.url.includes(SHARED_PREFIX)) {
    return next(req);
  }

  const session = inject(Session);
  const api = inject(Api);
  const router = inject(Router);

  const withToken = (request: HttpRequest<unknown>, token: string | null) =>
    token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(withToken(req, session.accessToken())).pipe(
    catchError((error: unknown) => {
      const refreshToken = session.refreshToken();
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !refreshToken) {
        return throwError(() => error);
      }

      refreshInFlight ??= api.refresh(refreshToken).pipe(
        finalize(() => (refreshInFlight = null)),
        shareReplay(1),
      );

      return refreshInFlight.pipe(
        catchError((refreshError: unknown) => {
          session.clear();
          void router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
        switchMap((tokens) => {
          session.store(tokens);
          // A second 401 here reaches the caller as a normal error: the session itself is still good
          return next(withToken(req, tokens.accessToken));
        }),
      );
    }),
  );
};
