import { Component, computed, input, output } from '@angular/core';
import { errorMessage } from '../core/errors';
import { Icon } from './icon';

// What a page shows while a resource has no value to render: a placeholder, or the failure with a retry
@Component({
  selector: 'app-load-state',
  imports: [Icon],
  template: `
    @if (error()) {
      <div class="banner banner-error" role="alert">
        <app-icon name="alert" />
        <div style="flex:1">{{ message() }}</div>
        <button type="button" class="btn btn-small" (click)="retry.emit()">Try again</button>
      </div>
    } @else {
      <span class="skeleton" role="status" aria-label="Loading"></span>
    }
  `,
})
export class LoadState {
  readonly error = input<unknown>(undefined);
  readonly retry = output<void>();
  protected readonly message = computed(() => errorMessage(this.error()));
}
