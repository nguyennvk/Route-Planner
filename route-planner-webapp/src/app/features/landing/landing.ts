import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Session } from '../../core/session';
import { Brand } from '../../shared/brand';
import { Icon, IconName } from '../../shared/icon';

// Clearly labelled sample data for the run sheet preview. Not real customers.
const SAMPLE_STOPS = [
  { time: '08:34', name: 'Harbourfront Deli', window: '08:00 – 10:00', kg: '42' },
  { time: '09:02', name: 'Queen St Books', window: 'Any time', kg: '18' },
  { time: '09:41', name: 'Union Print Shop', window: '09:30 – 10:00', kg: '120', strict: true },
  { time: '10:15', name: 'Annex Community Hall', window: '10:00 – 12:00', kg: '64' },
];

const STEPS = [
  { title: 'Set up your fleet', text: 'Depots, vehicles with their weight limits, and the shifts that put them on the road.' },
  { title: 'Take your orders', text: 'Pick a customer and items. The weight adds itself up, and you set the delivery window.' },
  { title: 'Choose the day', text: 'Tick the shifts and pending orders you want planned. Leave out whatever can wait.' },
  { title: 'Solve', text: 'Get every vehicle a run sheet with stops in order, arrival times and a map.' },
];

const DETAILS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'pin', title: 'Addresses that land in the right spot', text: 'Search as you type. When a match isn\'t exact, place the pin yourself, and move it later if a driver finds a better entrance. Address search covers Canada.' },
  { icon: 'clock', title: "Your company's clock", text: 'Shifts, windows and arrivals all show in your company\'s timezone, wherever you open the app from.' },
  { icon: 'truck', title: 'No double-booking', text: 'A vehicle or driver with overlapping shifts is caught when you save, before it can reach a plan.' },
  { icon: 'building', title: 'One login, several companies', text: 'Run more than one operation from the same account, each with its own fleet, customers and team.' },
];

@Component({
  selector: 'app-landing',
  imports: [RouterLink, Brand, Icon],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {
  protected readonly session = inject(Session);
  protected readonly stops = SAMPLE_STOPS;
  protected readonly steps = STEPS;
  protected readonly details = DETAILS;
}
