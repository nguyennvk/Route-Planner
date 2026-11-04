import { Component } from '@angular/core';

@Component({
  selector: 'app-auth-art',
  template: `
    <svg viewBox="0 0 600 800" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <g fill="none" stroke="#fff" stroke-opacity=".1" stroke-width="2">
        <path d="M0 140H600M0 300H600M0 460H600M120 0V800M300 0V800M480 0V800" />
      </g>
      <path d="M120 620V300a40 40 0 0 1 40-40H300a40 40 0 0 0 40-40V100" fill="none" stroke="#ffb81c" stroke-width="7" stroke-linecap="round" />
      <path d="M120 620V460a0 0 0 0 1 0 0H440a40 40 0 0 0 40-40V300" fill="none" stroke="#fff" stroke-width="7" stroke-linecap="round" />
      <g fill="#fff" stroke="#00593f" stroke-width="4">
        <circle cx="300" cy="260" r="10" /><circle cx="340" cy="100" r="10" /><circle cx="300" cy="460" r="10" /><circle cx="480" cy="300" r="10" />
      </g>
      <rect x="104" y="604" width="32" height="32" rx="6" fill="#fff" />
    </svg>
  `,
  styles: `svg { position: absolute; inset: 0; width: 100%; height: 100%; }`,
  host: { style: 'display:contents' },
})
export class AuthArt {}
