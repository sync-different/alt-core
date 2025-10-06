/**
 * Validation utility functions
 */

/**
 * Validate email address format
 * @param email - Email address to validate
 * @returns true if valid, false otherwise
 */
export const validateEmail = (email: string): boolean => {
  if (!email || typeof email !== 'string') {
    return false;
  }

  // RFC 5322 compliant email regex (simplified version)
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email.trim());
};

/**
 * Validate tag name format
 * Rules:
 * - Must be at least 1 character
 * - Can contain letters, numbers, underscores, hyphens
 * - Cannot start or end with whitespace
 * - Maximum 50 characters
 * @param tag - Tag name to validate
 * @returns true if valid, false otherwise
 */
export const validateTag = (tag: string): boolean => {
  if (!tag || typeof tag !== 'string') {
    return false;
  }

  const trimmed = tag.trim();

  // Check length
  if (trimmed.length === 0 || trimmed.length > 50) {
    return false;
  }

  // Check if tag contains only valid characters (letters, numbers, spaces, underscores, hyphens)
  const tagRegex = /^[a-zA-Z0-9\s_-]+$/;
  return tagRegex.test(trimmed);
};

/**
 * Validate username format
 * Rules:
 * - Must be 3-30 characters
 * - Can contain letters, numbers, underscores, hyphens
 * - Must start with a letter
 * @param username - Username to validate
 * @returns true if valid, false otherwise
 */
export const validateUsername = (username: string): boolean => {
  if (!username || typeof username !== 'string') {
    return false;
  }

  const trimmed = username.trim();

  // Check length
  if (trimmed.length < 3 || trimmed.length > 30) {
    return false;
  }

  // Must start with a letter and contain only letters, numbers, underscores, hyphens
  const usernameRegex = /^[a-zA-Z][a-zA-Z0-9_-]*$/;
  return usernameRegex.test(trimmed);
};

/**
 * Validate password strength
 * Rules:
 * - Minimum 8 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one number
 * @param password - Password to validate
 * @returns Object with valid flag and error message
 */
export const validatePassword = (
  password: string
): { valid: boolean; message?: string } => {
  if (!password || typeof password !== 'string') {
    return { valid: false, message: 'Password is required' };
  }

  if (password.length < 8) {
    return { valid: false, message: 'Password must be at least 8 characters' };
  }

  if (!/[A-Z]/.test(password)) {
    return { valid: false, message: 'Password must contain an uppercase letter' };
  }

  if (!/[a-z]/.test(password)) {
    return { valid: false, message: 'Password must contain a lowercase letter' };
  }

  if (!/[0-9]/.test(password)) {
    return { valid: false, message: 'Password must contain a number' };
  }

  return { valid: true };
};

/**
 * Validate file extension
 * @param extension - File extension (with or without leading dot)
 * @returns true if valid, false otherwise
 */
export const validateFileExtension = (extension: string): boolean => {
  if (!extension || typeof extension !== 'string') {
    return false;
  }

  // Remove leading dot if present
  const ext = extension.startsWith('.') ? extension.slice(1) : extension;

  // Check if extension contains only valid characters
  const extRegex = /^[a-zA-Z0-9]+$/;
  return extRegex.test(ext) && ext.length > 0 && ext.length <= 10;
};

/**
 * Validate IP address (IPv4)
 * @param ip - IP address to validate
 * @returns true if valid IPv4, false otherwise
 */
export const validateIPv4 = (ip: string): boolean => {
  if (!ip || typeof ip !== 'string') {
    return false;
  }

  const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
  if (!ipv4Regex.test(ip)) {
    return false;
  }

  // Check each octet is 0-255
  const octets = ip.split('.');
  return octets.every((octet) => {
    const num = parseInt(octet, 10);
    return num >= 0 && num <= 255;
  });
};

/**
 * Validate port number
 * @param port - Port number to validate
 * @returns true if valid (1-65535), false otherwise
 */
export const validatePort = (port: number | string): boolean => {
  const portNum = typeof port === 'string' ? parseInt(port, 10) : port;

  if (isNaN(portNum)) {
    return false;
  }

  return portNum >= 1 && portNum <= 65535;
};
