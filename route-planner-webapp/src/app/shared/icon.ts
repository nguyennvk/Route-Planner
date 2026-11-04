import { Component, ElementRef, effect, inject, input } from '@angular/core';
import {
  ArrowLeft, ArrowRight, Boxes, Building2, ChevronLeft, ChevronRight, CircleAlert, CircleCheck, Clock, IconNode,
  Info, LayoutDashboard, LogOut, MapPin, Menu, Package, Pencil, Plus, Route, Search, Settings, Trash2, TriangleAlert,
  Truck, Users, UserRound, Warehouse, X, createElement,
} from 'lucide';

const ICONS = {
  'arrow-left': ArrowLeft, 'arrow-right': ArrowRight, boxes: Boxes, building: Building2, 'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight, alert: CircleAlert, check: CircleCheck, clock: Clock, info: Info,
  dashboard: LayoutDashboard, logout: LogOut, pin: MapPin, menu: Menu, package: Package, pencil: Pencil, plus: Plus,
  route: Route, search: Search, settings: Settings, trash: Trash2, warning: TriangleAlert, truck: Truck, users: Users,
  user: UserRound, depot: Warehouse, close: X,
} satisfies Record<string, IconNode>;

export type IconName = keyof typeof ICONS;

// Importing icons by name keeps the bundle to these few instead of Lucide's full set
@Component({
  selector: 'app-icon',
  template: '',
  host: { style: 'display:inline-flex;flex:none', 'aria-hidden': 'true' },
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(16);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    effect(() => {
      host.replaceChildren(createElement(ICONS[this.name()], { width: this.size(), height: this.size(), 'stroke-width': 2 }));
    });
  }
}
