import { Injectable, computed, signal } from '@angular/core';
import { LoginResponse } from './models';

const KEY = 'route-scheduler.session';

interface Tokens {
  accessToken: string;
  refreshToken: string;
}

// Holds the tokens. localStorage keeps someone logged in across reloads, but any script on the page can
// read it, so an XSS bug would leak them. Angular escapes template values by default, which limits that risk;
// HttpOnly cookies would remove it but need the API to set them.
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly tokens = signal<Tokens | null>(this.read());

  readonly loggedIn = computed(() => this.tokens() !== null);
  readonly accessToken = computed(() => this.tokens()?.accessToken ?? null);
  readonly refreshToken = computed(() => this.tokens()?.refreshToken ?? null);

  store(response: LoginResponse): void {
    const tokens = { accessToken: response.accessToken, refreshToken: response.refreshToken };
    localStorage.setItem(KEY, JSON.stringify(tokens));
    this.tokens.set(tokens);
  }

  clear(): void {
    localStorage.removeItem(KEY);
    this.tokens.set(null);
  }

  private read(): Tokens | null {
    try {
      return JSON.parse(localStorage.getItem(KEY) ?? 'null');
    } catch {
      return null;
    }
  }
}
