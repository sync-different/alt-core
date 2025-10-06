/**
 * URL Helper Utilities
 * Provides functions to build URLs that work in both development and production
 */

/**
 * Get the base URL for API requests
 * In development: http://localhost:8081
 * In production: empty string (uses same origin)
 */
export function getApiBaseUrl(): string {
  return import.meta.env.DEV ? 'http://localhost:8081' : '';
}

/**
 * Build a full URL for a given path
 * Automatically adds the correct base URL based on environment
 *
 * @param path - The path (e.g., '/cass/getfile.fn?md5=123')
 * @returns Full URL
 */
export function buildUrl(path: string): string {
  const baseUrl = getApiBaseUrl();

  // If path already starts with http, return as-is
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path;
  }

  // Ensure path starts with /
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  return `${baseUrl}${normalizedPath}`;
}
