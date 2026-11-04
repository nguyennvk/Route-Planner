export type SignupRole = 'owner' | 'driver';

// The API has no account-level role: you're an owner or driver per company, through company_members.
// This only remembers which first screen someone asked for, so it lives in the browser. On another
// device it's simply missing, and the screen offers both paths.
const key = (userId: number) => `route-scheduler.signup-role.${userId}`;

export function rememberSignupRole(userId: number, role: SignupRole): void {
  localStorage.setItem(key(userId), role);
}

export function signupRole(userId: number): SignupRole | null {
  const stored = localStorage.getItem(key(userId));
  return stored === 'owner' || stored === 'driver' ? stored : null;
}
