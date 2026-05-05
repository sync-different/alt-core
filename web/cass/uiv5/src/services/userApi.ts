/**
 * User-management API wrappers for the Admin → Users tab.
 *
 * Lifted into its own module to avoid bloating shareService.ts further.
 * For backwards-compat with existing shareService consumers (ShareDialog,
 * ShareModal, SharesPage), `addUser` is re-exported from here in addition
 * to its original home.
 *
 * See internal/PROJECT_TAB_ADMIN_USERS.md.
 */

import { buildUrl } from '../utils/urlHelper';
import type { User } from './shareService';
export { addUser, type AddUserData } from './shareService';

/**
 * List all non-admin users with their emails. Uses existing
 * getusersandemail.fn (admin-only). The endpoint excludes admin accounts.
 */
export async function listUsers(): Promise<User[]> {
  const response = await fetch(buildUrl('/cass/getusersandemail.fn'), {
    credentials: 'include',
  });
  if (!response.ok) throw new Error('Failed to fetch users');
  const text = await response.text();
  if (!text || text.trim() === '') return [];
  try {
    const data = JSON.parse(text);
    return data.users || [];
  } catch {
    throw new Error('Invalid response from server');
  }
}

/**
 * Delete a non-admin user. Backend cleans up shares + invalidates
 * the user's active sessions automatically.
 *
 * Throws on backend errors (`forbidden`, `notfound`, `error`).
 */
export async function deleteUser(username: string): Promise<void> {
  // Server parses `boxuser=` from the query string (line 2106 in WebServer.java);
  // the server-side variable is named sBoxUser but the URL param is lowercase.
  const url = buildUrl(
    `/cass/deluser.fn?boxuser=${encodeURIComponent(username)}`,
  );
  const response = await fetch(url, { credentials: 'include' });
  if (!response.ok) throw new Error('Failed to delete user');
  const result = (await response.text()).trim().toLowerCase();
  if (result !== 'success') {
    throw new Error(result || 'unknown');
  }
}

/**
 * Admin-override password set for a NON-ADMIN user. Skips old-password
 * verification. Backend invalidates the target user's active sessions
 * (per Q5 — credential change forces re-login).
 *
 * Use {@link setAdminPassword} for the admin's own password.
 */
export async function setUserPassword(
  username: string,
  newPassword: string,
): Promise<void> {
  const url = buildUrl(
    `/cass/setuserpassword.fn?boxuser=${encodeURIComponent(username)}&boxpass=${encodeURIComponent(newPassword)}`,
  );
  const response = await fetch(url, { credentials: 'include' });
  if (!response.ok) throw new Error('Failed to set password');
  const result = (await response.text()).trim().toLowerCase();
  if (result !== 'success') {
    throw new Error(result || 'unknown');
  }
}

/**
 * Set a user's email (admin or non-admin — email isn't a credential, no
 * special protection). Does NOT invalidate sessions (per Q6).
 */
export async function setUserEmail(
  username: string,
  newEmail: string,
): Promise<void> {
  const url = buildUrl(
    `/cass/setuseremail.fn?boxuser=${encodeURIComponent(username)}&useremail=${encodeURIComponent(newEmail)}`,
  );
  const response = await fetch(url, { credentials: 'include' });
  if (!response.ok) throw new Error('Failed to set email');
  const result = (await response.text()).trim().toLowerCase();
  if (result !== 'success') {
    throw new Error(result || 'unknown');
  }
}

/**
 * Admin self-service password change. The session-cookie identifies the
 * caller, so no username param is needed. Backend invalidates the admin's
 * OTHER sessions (the calling session keeps its UUID so the UI doesn't
 * log itself out).
 */
export async function setAdminPassword(newPassword: string): Promise<void> {
  const url = buildUrl(
    `/cass/setadminpassword.fn?boxpass=${encodeURIComponent(newPassword)}`,
  );
  const response = await fetch(url, { credentials: 'include' });
  if (!response.ok) throw new Error('Failed to set admin password');
  const result = (await response.text()).trim().toLowerCase();
  if (result !== 'success') {
    throw new Error(result || 'unknown');
  }
}
