import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { ProblemDetail } from './models';

// rxResource wraps anything that isn't a real Error, and HttpErrorResponse isn't one
function unwrap(error: unknown): unknown {
  return error instanceof Error && error.cause ? error.cause : error;
}

// Turns GlobalExceptionHandler's ProblemDetail into a sentence for the screen
export function errorMessage(rawError: unknown): string {
  const error = unwrap(rawError);
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as ProblemDetail | null;
    // 0: nothing answered. 500/502/504 with no ProblemDetail: the dev proxy couldn't reach the API.
    if (error.status === 0 || (error.status >= 500 && !problem?.detail)) {
      return "Can't reach the Route Scheduler API. Check that it's running, then try again.";
    }
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.status === 403) {
      return 'Only the company owner can do this.';
    }
  }
  return 'Something went wrong. Please try again.';
}

// Puts Bean Validation messages on the matching inputs; returns false if none of them matched a control
export function applyFieldErrors(form: FormGroup, error: unknown): boolean {
  const violations = error instanceof HttpErrorResponse ? (error.error as ProblemDetail | null)?.errors : undefined;
  let matched = false;
  for (const violation of violations ?? []) {
    const control = form.get(violation.field);
    if (control) {
      control.setErrors({ server: violation.message });
      control.markAsTouched();
      matched = true;
    }
  }
  return matched;
}
