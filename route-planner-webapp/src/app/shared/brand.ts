import { Component, input } from '@angular/core';

@Component({
  selector: 'app-brand',
  template: `
    <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
      <rect width="32" height="32" rx="7" [attr.fill]="onDark() ? '#fff' : '#00593f'" />
      <path d="M9 23V15a4 4 0 0 1 4-4h10" fill="none" [attr.stroke]="onDark() ? '#00593f' : '#ffb81c'" stroke-width="3" stroke-linecap="round" />
      <circle cx="9" cy="23" r="3" [attr.fill]="onDark() ? '#00593f' : '#fff'" />
      <circle cx="23" cy="11" r="3" [attr.fill]="onDark() ? '#00593f' : '#fff'" />
    </svg>
    <span>Route Scheduler</span>
  `,
  host: { style: 'display:inline-flex;align-items:center;gap:9px;font-weight:800;font-size:17px;letter-spacing:-0.01em;white-space:nowrap' },
})
export class Brand {
  readonly onDark = input(false);
}
