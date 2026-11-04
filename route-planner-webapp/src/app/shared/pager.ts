import { Component, input, output } from '@angular/core';
import { Icon } from './icon';

// page is zero-based, like the API's PageResponse
@Component({
  selector: 'app-pager',
  imports: [Icon],
  template: `
    @if (totalPages() > 1) {
      <nav class="pager" aria-label="Pagination">
        <span class="muted small">{{ total() }} total · page {{ page() + 1 }} of {{ totalPages() }}</span>
        <button type="button" class="btn btn-small btn-icon" [disabled]="page() === 0" aria-label="Previous page" (click)="pageChange.emit(page() - 1)"><app-icon name="chevron-left" /></button>
        <button type="button" class="btn btn-small btn-icon" [disabled]="page() + 1 >= totalPages()" aria-label="Next page" (click)="pageChange.emit(page() + 1)"><app-icon name="chevron-right" /></button>
      </nav>
    }
  `,
  styles: `.pager { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 12px; }`,
})
export class Pager {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly total = input(0);
  readonly pageChange = output<number>();
}
