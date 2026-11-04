import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { signupRole } from '../../core/signup-role';
import { Register } from './register';

describe('Register', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'app', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  async function render(role?: string) {
    const fixture = TestBed.createComponent(Register);
    if (role) fixture.componentRef.setInput('role', role);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  const checkedRole = (element: HTMLElement) => element.querySelector<HTMLInputElement>('input[name=role]:checked')?.value;

  it('starts on owner, and a sign-up link can preselect driver', async () => {
    expect(checkedRole((await render()).element)).toBe('owner');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    expect(checkedRole((await render('driver')).element)).toBe('driver');
  });

  it('ignores a role it does not recognise', async () => {
    expect(checkedRole((await render('admin')).element)).toBe('owner');
  });

  it('keeps the role out of the API call and remembers it for the first screen', async () => {
    const { fixture, element } = await render();
    element.querySelector<HTMLInputElement>('input[value=driver]')!.click();
    await fixture.whenStable();

    const fill = (name: string, value: string) => {
      const input = element.querySelector<HTMLInputElement>(`[formControlName=${name}]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    fill('fullname', 'Dana Driver');
    fill('email', 'dana@example.com');
    fill('phone', '+14165550100');
    fill('password', 'long-enough-password');
    element.querySelector('form')!.dispatchEvent(new Event('submit'));

    const backend = TestBed.inject(HttpTestingController);
    const register = backend.expectOne('/api/v1/auth/register');
    expect(register.request.body).toEqual({ fullname: 'Dana Driver', email: 'dana@example.com', phone: '+14165550100', password: 'long-enough-password' });
    register.flush({ id: 42 });
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 });

    expect(signupRole(42)).toBe('driver');
  });
});
