import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { rememberSignupRole } from '../../core/signup-role';
import { CompanyList } from './company-list';

const ME = { id: 7, email: 'dana@example.com', fullName: 'Dana', phone: '+14165550100', createdAt: '' };
const COMPANY = { id: 1, name: 'Maple Water Co.', timezone: 'America/Toronto', createdAt: '' };

describe('CompanyList', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  async function render(companies: unknown[]) {
    const fixture = TestBed.createComponent(CompanyList);
    // tick() rather than whenStable(): stability waits for these requests, which only finish once flushed below
    TestBed.tick();
    backend.expectOne('/api/v1/companies').flush(companies);
    backend.expectOne('/api/v1/auth/me').flush(ME);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('shows a signed-up driver with no company the email to give their owner', async () => {
    rememberSignupRole(ME.id, 'driver');
    const { element } = await render([]);

    expect(element.querySelector('h1')?.textContent).toContain("You're signed up");
    expect(element.textContent).toContain(ME.email);
    expect(element.querySelector('form')).toBeNull();
  });

  it('shows everyone else the company form, including when no role was remembered', async () => {
    const { element } = await render([]);

    expect(element.querySelector('h1')?.textContent).toContain('Set up your company');
    expect(element.querySelector('form')).not.toBeNull();
  });

  it('lets a driver switch to setting up their own company', async () => {
    rememberSignupRole(ME.id, 'driver');
    const { fixture, element } = await render([]);

    [...element.querySelectorAll('button')].find((b) => b.textContent?.includes('Set up a company'))!.click();
    await fixture.whenStable();

    expect(element.querySelector('form')).not.toBeNull();
  });

  it('shows a driver their company once they have been added', async () => {
    rememberSignupRole(ME.id, 'driver');
    const { fixture, element } = await render([]);

    [...element.querySelectorAll('button')].find((b) => b.textContent?.includes('Check again'))!.click();
    TestBed.tick();
    backend.expectOne('/api/v1/companies').flush([COMPANY]);
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('Your companies');
    expect(element.textContent).toContain(COMPANY.name);
  });
});
