import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Observable } from 'rxjs';
import { applyFieldErrors, errorMessage } from '../core/errors';

// The submit lifecycle every form repeats: busy flag, call, then field errors or a form-level message
export class FormRunner {
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  // statusMessages replaces the server's text for a status, when the page can say something more useful
  run<T>(source: Observable<T>, onSuccess: (value: T) => void, form?: FormGroup, statusMessages: Record<number, string> = {}): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: (value) => {
        this.busy.set(false);
        onSuccess(value);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const replacement = error instanceof HttpErrorResponse ? statusMessages[error.status] : undefined;
        if (replacement) {
          this.error.set(replacement);
        } else if (!form || !applyFieldErrors(form, error)) {
          this.error.set(errorMessage(error));
        }
      },
    });
  }

  reset(): void {
    this.error.set(null);
  }
}
