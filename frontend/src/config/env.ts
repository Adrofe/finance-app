export const KEYCLOAK_BASE_URL = import.meta.env.VITE_KEYCLOAK_BASE_URL || 'http://localhost:8080';
export const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'finance-app';
export const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'finance-client';
export const KEYCLOAK_CLIENT_SECRET = import.meta.env.VITE_KEYCLOAK_CLIENT_SECRET || '';

export const TOKEN_STORAGE_KEY = 'finance_app_access_token';
