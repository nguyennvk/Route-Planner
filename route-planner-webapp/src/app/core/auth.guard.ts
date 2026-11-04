import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from './session';

// Only decides what to show. The API still checks the token on every request, as SecurityConfig does.
export const requireLogin: CanActivateFn = () =>
  inject(Session).loggedIn() || inject(Router).createUrlTree(['/login']);

export const requireLogout: CanActivateFn = () =>
  !inject(Session).loggedIn() || inject(Router).createUrlTree(['/app']);
