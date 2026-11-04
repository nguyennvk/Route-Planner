import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { errorMessage } from '../../core/errors';
import { MEMBER_ROLES, Member, MemberRole } from '../../core/models';
import { Confirm } from '../../shared/confirm';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Modal } from '../../shared/modal';

@Component({
  selector: 'app-team',
  imports: [ReactiveFormsModule, Confirm, Field, Icon, LoadState, Modal],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Team</h1><p>Add people by the email on their Route Scheduler account. Drivers can then be assigned to shifts.</p></div>
        <button type="button" class="btn btn-primary" (click)="openAdd()"><app-icon name="plus" /> Add member</button>
      </div>
      @if (actionError()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ actionError() }}</div> }

      @if (members.hasValue()) {
        <div class="table-wrap"><table class="data">
          <thead><tr><th>Name</th><th>Role</th><th>Status</th><th><span class="visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            @for (member of members.value(); track member.userId) {
              <tr>
                <td>{{ member.fullName }}<span class="cell-sub">{{ member.email }}</span></td>
                <td>
                  @if (isMe(member)) {
                    <span class="cap">{{ member.role }}</span>
                  } @else {
                    <select class="input role" [value]="member.role" [attr.aria-label]="'Role for ' + member.fullName" (change)="setRole(member, $any($event.target).value)">
                      @for (role of roles; track role) { <option [value]="role" [selected]="role === member.role">{{ role }}</option> }
                    </select>
                  }
                </td>
                <td>
                  @if (member.active) { <span class="badge badge-ok">Active</span> } @else { <span class="badge">Inactive</span> }
                </td>
                <td class="row-actions">
                  @if (isMe(member)) {
                    <span class="small muted">You</span>
                  } @else {
                    <button type="button" class="btn btn-small" (click)="toggleActive(member)">{{ member.active ? 'Deactivate' : 'Reactivate' }}</button>
                    <button type="button" class="btn btn-small btn-danger" (click)="removing.set(member)">Remove</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table></div>
      } @else {
        <app-load-state [error]="members.error()" (retry)="members.reload()" />
      }
    </div>

    <app-modal [open]="adding()" heading="Add member" (closed)="adding.set(false)">
      <form [formGroup]="form" (ngSubmit)="add()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ runner.error() }}</div> }
        <div class="form-grid">
          <app-field class="span-2" label="Email" [control]="form.controls.email"><input class="input" type="email" formControlName="email" /></app-field>
          <app-field class="span-2" label="Role" [control]="form.controls.role">
            <select class="input" formControlName="role">
              @for (role of roles; track role) { <option [value]="role">{{ role }}</option> }
            </select>
          </app-field>
        </div>
        <p class="small muted signup-link">Not signed up yet? Send drivers this link: <span class="mono">{{ driverSignupUrl }}</span></p>
        <div class="form-actions">
          <button type="button" class="btn" (click)="adding.set(false)">Cancel</button>
          <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Adding…' : 'Add member' }}</button>
        </div>
      </form>
    </app-modal>

    <app-confirm [open]="removing() !== null" heading="Remove from company?" [message]="removeMessage()" action="Remove member"
      [busy]="runner.busy()" [error]="runner.error()" (cancelled)="removing.set(null)" (confirmed)="remove()" />
  `,
  styles: `
    .role { width: auto; min-width: 120px; min-height: 32px; padding-block: 3px 1px; text-transform: capitalize; }
    .cap { text-transform: capitalize; }
    .signup-link { margin-top: 12px; overflow-wrap: anywhere; }
  `,
})
export class Team {
  private readonly api = inject(Api);
  private readonly ctx = inject(CompanyContext);

  protected readonly roles = MEMBER_ROLES;
  protected readonly driverSignupUrl = new URL('register?role=driver', document.baseURI).href;
  protected readonly members = rxResource({ params: () => this.ctx.id(), stream: ({ params: id }) => this.api.members(id) });
  private readonly me = rxResource({ stream: () => this.api.me() });

  // CompanyService refuses changes to your own membership, so don't offer them
  protected isMe(member: Member): boolean {
    return this.me.hasValue() && this.me.value().id === member.userId;
  }

  protected readonly runner = new FormRunner();
  protected readonly actionError = signal<string | null>(null);
  protected readonly adding = signal(false);
  protected readonly removing = signal<Member | null>(null);
  protected readonly removeMessage = computed(() => `${this.removing()?.fullName ?? ''} will lose access to this company.`);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['driver' as MemberRole, Validators.required],
  });

  protected openAdd(): void {
    this.form.reset();
    this.runner.reset();
    this.adding.set(true);
  }

  protected add(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.runner.run(this.api.addMember(this.ctx.id(), this.form.getRawValue()), () => {
      this.adding.set(false);
      this.members.reload();
    }, this.form, { 404: "No account uses this email yet. Send them the sign-up link below, then add them." });
  }

  protected setRole(member: Member, role: MemberRole): void { this.patch(member, { role }); }
  protected toggleActive(member: Member): void { this.patch(member, { active: !member.active }); }

  private patch(member: Member, body: { role?: MemberRole; active?: boolean }): void {
    this.actionError.set(null);
    this.api.updateMember(this.ctx.id(), member.userId, body).subscribe({
      next: () => this.members.reload(),
      error: (error: unknown) => {
        this.actionError.set(errorMessage(error));
        // Puts the role dropdown back to what the server still has
        this.members.reload();
      },
    });
  }

  protected remove(): void {
    const member = this.removing();
    if (!member) return;
    this.runner.reset();
    this.runner.run(this.api.removeMember(this.ctx.id(), member.userId), () => {
      this.removing.set(null);
      this.members.reload();
    });
  }
}
