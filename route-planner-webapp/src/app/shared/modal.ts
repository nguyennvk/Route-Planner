import { Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { Icon } from './icon';

// Wraps the native <dialog>: showModal() brings focus trapping, Esc to close and the backdrop for free
@Component({
  selector: 'app-modal',
  imports: [Icon],
  template: `
    <dialog #dialog [class.wide]="wide()" (cancel)="$event.preventDefault(); closed.emit()" (click)="closeOnBackdrop($event)">
      <div class="body">
        <header>
          <h2>{{ heading() }}</h2>
          <button type="button" class="btn btn-quiet btn-icon" aria-label="Close" (click)="closed.emit()"><app-icon name="close" /></button>
        </header>
        <ng-content />
      </div>
    </dialog>
  `,
  styles: `
    dialog { width: min(520px, calc(100vw - 24px)); max-height: calc(100dvh - 32px); padding: 0; border: 0; border-radius: var(--radius-l); box-shadow: var(--shadow-pop); color: var(--ink); }
    dialog.wide { width: min(760px, calc(100vw - 24px)); }
    dialog::backdrop { background: rgb(3 30 22 / 55%); }
    .body { padding: 18px 20px 20px; }
    header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
    h2 { font-size: 19px; }
  `,
})
export class Modal {
  readonly open = input.required<boolean>();
  readonly heading = input.required<string>();
  readonly wide = input(false);
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        element.showModal();
        // showModal() focuses the first focusable element, which would be the close button
        element.querySelector<HTMLElement>('input:not([type=checkbox]), select, textarea')?.focus();
      }
      if (!this.open() && element.open) element.close();
    });
  }

  protected closeOnBackdrop(event: MouseEvent): void {
    // A click on the padding-less <dialog> itself, rather than its content, is a click on the backdrop
    if (event.target === this.dialog().nativeElement) this.closed.emit();
  }
}
