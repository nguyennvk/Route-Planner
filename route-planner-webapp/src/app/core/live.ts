import { Injectable, inject } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable, defer, map } from 'rxjs';
import { PositionMessage } from './models';
import { Session } from './session';

// The WebSocket counterpart of Api: one STOMP connection for the whole app, one method per topic.
// Like HttpClient calls, nothing happens until something subscribes.
@Injectable({ providedIn: 'root' })
export class Live {
  private readonly session = inject(Session);
  private readonly stomp = new RxStomp();
  private active = false;

  // ---- Topics: add one line per destination, like the endpoints in api.ts ----
  routePositions(routeId: number) { return this.watch<PositionMessage>(`/topic/routes/${routeId}/position`); }

  // ---- Plumbing ----

  // Each emitted value is the JSON body of one MESSAGE frame.
  // rx-stomp sends SUBSCRIBE on subscribe, UNSUBSCRIBE on unsubscribe, and re-subscribes after a reconnect.
  private watch<T>(destination: string): Observable<T> {
    return defer(() => {
      this.connect();                                   // first subscriber opens the connection
      return this.stomp.watch(destination);
    }).pipe(map((message) => JSON.parse(message.body) as T));
  }

  private connect(): void {
    if (this.active) return;
    this.active = true;
    this.stomp.configure({
      // Same host as the page, so it goes through the proxy in dev and nginx in production
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      reconnectDelay: 5_000,
      // Runs before every CONNECT, reconnects included, so a refreshed access token is used
      beforeConnect: (client) => {
        client.configure({ connectHeaders: { Authorization: `Bearer ${this.session.accessToken() ?? ''}` } });
      },
    });
    this.stomp.stompErrors$.subscribe((frame) => console.warn('STOMP error:', frame.headers['message']));
    this.stomp.activate();
  }

  // Call on logout: the open connection was authenticated as the old user
  disconnect(): void {
    if (!this.active) return;
    this.active = false;
    void this.stomp.deactivate();
  }
}
