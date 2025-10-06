/**
 * Axios API client for Alterante backend
 * Base URL: /cass
 * Handles authentication, request/response interceptors
 */

import axios from 'axios';
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios';

// Create axios instance
// In development, Vite proxy will forward requests to backend
// In production, requests go to same origin
const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.DEV ? 'http://localhost:8081' : '',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add UUID and default view parameter
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Initialize params if not exists
    if (!config.params) {
      config.params = {};
    }

    // Add UUID from localStorage to query params (more reliable than cookies in dev)
    // Only add if not already present
    const uuid = localStorage.getItem('uuid');
    if (uuid && !config.params.uuid) {
      config.params.uuid = uuid;
    }

    // Add default view parameter for JSON responses
    if (!config.params.view) {
      config.params.view = 'json';
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor - handle 401 (redirect to login)
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response;
  },
  (error) => {
    // Handle 401 Unauthorized - redirect to login
    if (error.response && error.response.status === 401) {
      // Clear authentication
      localStorage.removeItem('uuid');
      document.cookie = 'uuid=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';

      // Redirect to login page
      window.location.href = '/login';
    }

    return Promise.reject(error);
  }
);

export default api;
