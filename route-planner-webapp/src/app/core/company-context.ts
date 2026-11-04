import { Injectable, computed, signal } from '@angular/core';
import { Company } from './models';
import { todayIn } from './time';

// The company being worked on and the day being looked at, shared by every page under /app/:companyId.
// A root-provided service is a singleton, like a default-scoped Spring bean.
@Injectable({ providedIn: 'root' })
export class CompanyContext {
  readonly company = signal<Company | null>(null);
  readonly isOwner = signal(false);
  readonly date = signal('');

  readonly id = computed(() => this.company()?.id ?? 0);
  readonly zone = computed(() => this.company()?.timezone ?? 'UTC');

  enter(company: Company, isOwner: boolean): void {
    const changedCompany = this.company()?.id !== company.id;
    this.company.set(company);
    this.isOwner.set(isOwner);
    if (changedCompany || !this.date()) {
      this.date.set(todayIn(company.timezone));
    }
  }

  link(...path: (string | number)[]): (string | number)[] {
    return ['/app', this.id(), ...path];
  }
}
