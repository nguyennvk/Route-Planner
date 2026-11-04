import { Component, computed, effect, input, signal } from '@angular/core';
import { AbstractControl } from '@angular/forms';

const MESSAGES: Record<string, (detail: any) => string> = {
  required: () => 'Required',
  email: () => 'Enter a valid email address',
  minlength: (e) => `Use at least ${e.requiredLength} characters`,
  maxlength: (e) => `Use at most ${e.requiredLength} characters`,
  min: (e) => `Must be ${e.min} or more`,
  max: (e) => `Must be ${e.max} or less`,
  pattern: () => 'Not in the expected format',
  server: (message) => message,
};

// A labelled input with its error message. The input goes inside <label>, which links the two
// without needing ids. control.events is watched because form state isn't a signal, so an
// OnPush component wouldn't otherwise redraw when a field is touched or fails validation.
@Component({
  selector: 'app-field',
  template: `
    <label class="field">
      <span class="field-label">{{ label() }}@if (optional()) { <span class="muted"> (optional)</span> }</span>
      <ng-content />
      @if (message(); as text) {
        <span class="field-error" role="alert">{{ text }}</span>
      } @else if (hint()) {
        <span class="field-hint">{{ hint() }}</span>
      }
    </label>
  `,
  host: { style: 'display:block;min-width:0' },
})
export class Field {
  readonly label = input.required<string>();
  readonly control = input<AbstractControl | null>(null);
  readonly hint = input('');
  readonly optional = input(false);
  readonly patternMessage = input('');

  private readonly version = signal(0);

  protected readonly message = computed(() => {
    this.version();
    const control = this.control();
    if (!control?.touched || !control.errors) return null;
    const [key, detail] = Object.entries(control.errors)[0];
    if (key === 'pattern' && this.patternMessage()) return this.patternMessage();
    return MESSAGES[key]?.(detail) ?? 'Invalid value';
  });

  constructor() {
    effect((onCleanup) => {
      const subscription = this.control()?.events.subscribe(() => this.version.update((v) => v + 1));
      onCleanup(() => subscription?.unsubscribe());
    });
  }
}
