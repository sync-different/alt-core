import { buildUrl } from '../utils/urlHelper';

export interface User {
  username: string;
  email: string;
  active?: boolean;
}

/**
 * Check if remote access is enabled
 */
export async function checkRemoteAccess(): Promise<boolean> {
  const url = buildUrl('/cass/serverproperty.fn?property=allowremote');
  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to check remote access');
  }

  const result = await response.text();
  return result.toLowerCase() !== 'false';
}

/**
 * Get cluster ID for remote access link
 */
export async function getClusterId(): Promise<string> {
  const url = buildUrl('/cass/getcluster.fn');
  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to get cluster ID');
  }

  const clusterId = await response.text();
  return clusterId.trim();
}

export interface ShareOptions {
  tagName: string;
  selectedUsers: string[];
  fileMD5s: string[];
}

export interface AddUserData {
  username: string;
  password: string;
  email: string;
}

/**
 * Fetch all users and their emails
 */
export async function getUsersAndEmails(): Promise<User[]> {
  const url = buildUrl('/cass/getusersandemail.fn');
  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to fetch users');
  }

  const text = await response.text();

  // Check if response is empty
  if (!text || text.trim() === '') {
    return [];
  }

  try {
    const data = JSON.parse(text);
    return data.users || [];
  } catch (e) {
    console.error('Failed to parse users response:', text);
    throw new Error('Invalid response from server');
  }
}

/**
 * Add a new user to the system
 */
export async function addUser(userData: AddUserData): Promise<void> {
  const { username, password, email } = userData;
  const url = buildUrl(
    `/cass/adduser.fn?boxuser=${encodeURIComponent(username)}&boxpass=${encodeURIComponent(password)}&useremail=${encodeURIComponent(email)}`
  );

  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to add user');
  }

  const result = await response.text();
  if (result.toLowerCase().includes('error')) {
    throw new Error(result);
  }
}

/**
 * Apply a tag to selected files
 */
export async function applyTag(tagName: string, fileMD5s: string[]): Promise<void> {
  if (fileMD5s.length === 0) {
    return;
  }

  // Build query string: md5value1=on&md5value2=on&...
  const md5Params = fileMD5s.map(md5 => `${md5}=on`).join('&');
  const url = buildUrl(`/cass/applyTag?tag=${encodeURIComponent(tagName)}&${md5Params}`);

  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to apply tag');
  }
}

/**
 * Share files with selected users
 */
export async function shareFiles(options: ShareOptions): Promise<string> {
  const { tagName, selectedUsers, fileMD5s } = options;

  // First, apply the tag to files
  await applyTag(tagName, fileMD5s);

  // Build users parameter: shareusers=user1%3Buser2%3Buser3
  // %3B is the URL encoding for semicolon (;)
  const usersParam = 'shareusers=' + selectedUsers.join('%3B');

  // Share the files
  const shareUrl = buildUrl(
    `/cass/doshare_webapp.fn?sharetype=TAG&${usersParam}&sharekey=${encodeURIComponent(tagName)}`
  );

  const response = await fetch(shareUrl, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to share files');
  }

  const token = await response.text();
  return token;
}

/**
 * Get share invitation email details
 */
export async function getShareInvitation(tagName: string): Promise<any> {
  const url = buildUrl(`/cass/invitation_webapp.fn?sharetype=TAG&sharekey=${encodeURIComponent(tagName)}`);

  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to get invitation');
  }

  const data = await response.json();
  return data.invitation;
}

export interface ActiveShare {
  sharetype: string;
  sharekey: string;
  shareusers?: string;
  createdate?: string;
}

export interface CreateShareOptions {
  shareType: 'TAG' | 'CLUSTER';
  shareKey?: string; // Tag name (required if shareType is TAG)
  selectedUsers: string[];
}

/**
 * Get all active shares
 */
export async function getActiveShares(): Promise<string> {
  const url = buildUrl('/cass/refreshsharetable.fn');
  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to fetch active shares');
  }

  // Returns HTML table
  return await response.text();
}

/**
 * Create a new share (TAG or CLUSTER)
 */
export async function createShare(options: CreateShareOptions): Promise<string> {
  const { shareType, shareKey, selectedUsers } = options;

  // Build users parameter
  const usersParam = selectedUsers.length > 0 ? selectedUsers.join(';') : '';

  // Build URL
  let url = `/cass/doshare.fn?sharetype=${shareType}&sharehtml=false`;

  if (shareType === 'TAG' && shareKey) {
    url += `&sharekey=${encodeURIComponent(shareKey)}`;
  } else if (shareType === 'CLUSTER') {
    url += `&sharekey=`;
  }

  if (usersParam) {
    url += `&shareusers=${encodeURIComponent(usersParam)}`;
  }

  const fullUrl = buildUrl(url);
  const response = await fetch(fullUrl, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to create share');
  }

  const result = await response.text();
  if (result === 'error') {
    throw new Error('Server returned error while creating share');
  }

  return result;
}

/**
 * Remove a share
 */
export async function removeShare(shareType: string, shareKey: string): Promise<string> {
  const url = buildUrl(
    `/cass/removeshare.fn?sharetype=${encodeURIComponent(shareType)}&sharekey=${encodeURIComponent(shareKey)}`
  );

  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to remove share');
  }

  // Returns updated HTML table
  return await response.text();
}

/**
 * Get all tags for share selection
 */
export async function getTags(): Promise<string[]> {
  // This endpoint might need to be implemented on the backend
  // For now, we'll return an empty array
  // TODO: Implement proper tag fetching
  return [];
}

export interface ShareSettings {
  sharetype: string;
  sharekey: string;
  users: User[];
}

/**
 * Get share settings (users with access)
 */
export async function getShareSettings(shareType: string, shareKey: string): Promise<ShareSettings> {
  const url = buildUrl(
    `/cass/getsharesettingsmodal.fn?sharetype=${encodeURIComponent(shareType)}&sharekey=${encodeURIComponent(shareKey)}`
  );

  const response = await fetch(url, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to fetch share settings');
  }

  // The endpoint returns HTML with checkboxes for users
  const html = await response.text();

  // Parse HTML to extract users from checkboxes
  // Format: <input type="checkbox" name="username" value="username" checked> username (email)
  const users: User[] = [];
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');

  const checkboxes = doc.querySelectorAll('input[type="checkbox"]');
  checkboxes.forEach((checkbox) => {
    // Only include users who have the 'checked' attribute (users with access)
    if (checkbox.hasAttribute('checked')) {
      const username = checkbox.getAttribute('name') || checkbox.getAttribute('value') || '';
      if (username) {
        // Find the label or text next to checkbox to get email
        const label = checkbox.parentElement?.textContent || '';
        const emailMatch = label.match(/\(([^)]+)\)/);
        const email = emailMatch ? emailMatch[1] : '';

        users.push({
          username,
          email,
          active: true
        });
      }
    }
  });

  return {
    sharetype: shareType,
    sharekey: shareKey,
    users
  };
}

/**
 * Update share settings (modify user access)
 */
export async function updateShare(shareType: string, shareKey: string, selectedUsers: string[]): Promise<void> {
  // Build users parameter
  const usersParam = selectedUsers.length > 0 ? selectedUsers.join(';') : '';

  // Build URL
  let url = `/cass/doshare.fn?sharetype=${shareType}&sharehtml=false&sharekey=${encodeURIComponent(shareKey)}`;

  if (usersParam) {
    url += `&shareusers=${encodeURIComponent(usersParam)}`;
  }

  const fullUrl = buildUrl(url);
  const response = await fetch(fullUrl, {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to update share');
  }

  const result = await response.text();
  if (result === 'error') {
    throw new Error('Server returned error while updating share');
  }
}
