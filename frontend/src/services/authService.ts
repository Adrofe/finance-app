import axios from 'axios';

import { KEYCLOAK_BASE_URL, KEYCLOAK_CLIENT_ID, KEYCLOAK_REALM } from '../config/env';
import type { KeycloakTokenResponse, PkceCallbackResult } from '../types/auth';

const PKCE_CODE_VERIFIER_KEY = 'finance_app_pkce_code_verifier';
const PKCE_STATE_KEY = 'finance_app_pkce_state';
const REFRESH_TOKEN_KEY = 'finance_app_refresh_token';

function toBase64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });

  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function createRandomString(size = 64): string {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return toBase64Url(bytes);
}

async function createCodeChallenge(codeVerifier: string): Promise<string> {
  const data = new TextEncoder().encode(codeVerifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return toBase64Url(new Uint8Array(digest));
}

function getRedirectUri(): string {
  return `${window.location.origin}/`;
}

export async function startPkceLogin(): Promise<void> {
  const codeVerifier = createRandomString(64);
  const state = createRandomString(32);
  const codeChallenge = await createCodeChallenge(codeVerifier);

  sessionStorage.setItem(PKCE_CODE_VERIFIER_KEY, codeVerifier);
  sessionStorage.setItem(PKCE_STATE_KEY, state);

  const authUrl = new URL(`${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`);
  authUrl.searchParams.set('response_type', 'code');
  authUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
  authUrl.searchParams.set('redirect_uri', getRedirectUri());
  authUrl.searchParams.set('scope', 'openid profile email');
  authUrl.searchParams.set('state', state);
  authUrl.searchParams.set('code_challenge', codeChallenge);
  authUrl.searchParams.set('code_challenge_method', 'S256');

  window.location.assign(authUrl.toString());
}

function clearPkceSessionState(): void {
  sessionStorage.removeItem(PKCE_CODE_VERIFIER_KEY);
  sessionStorage.removeItem(PKCE_STATE_KEY);
}

function clearAuthQueryParams(): void {
  window.history.replaceState({}, document.title, window.location.pathname);
}

export async function handlePkceCallback(): Promise<PkceCallbackResult> {
  const params = new URLSearchParams(window.location.search);
  const error = params.get('error');
  const errorDescription = params.get('error_description');
  const code = params.get('code');
  const state = params.get('state');

  if (error) {
    clearAuthQueryParams();
    clearPkceSessionState();
    return {
      completed: true,
      accessToken: null,
      error: errorDescription || error
    };
  }

  if (!code) {
    return {
      completed: false,
      accessToken: null,
      error: null
    };
  }

  const storedState = sessionStorage.getItem(PKCE_STATE_KEY);
  const codeVerifier = sessionStorage.getItem(PKCE_CODE_VERIFIER_KEY);

  if (!state || !storedState || state !== storedState || !codeVerifier) {
    clearAuthQueryParams();
    clearPkceSessionState();
    return {
      completed: true,
      accessToken: null,
      error: 'Invalid PKCE state. Please try login again.'
    };
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: KEYCLOAK_CLIENT_ID,
    code,
    redirect_uri: getRedirectUri(),
    code_verifier: codeVerifier
  });

  const tokenUrl = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
  const response = await axios.post<KeycloakTokenResponse>(tokenUrl, body.toString(), {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });

  // store refresh token if provided temporarily in session storage
  if (response.data.refresh_token) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, response.data.refresh_token);
  }

  clearAuthQueryParams();
  clearPkceSessionState();

  return {
    completed: true,
    accessToken: response.data.access_token,
    refreshToken: response.data.refresh_token || null,
    error: null
  };
}

export async function refreshAccessToken(refreshToken: string): Promise<KeycloakTokenResponse> {
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: KEYCLOAK_CLIENT_ID,
    refresh_token: refreshToken
  });

  const tokenUrl = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
  const response = await axios.post<KeycloakTokenResponse>(tokenUrl, body.toString(), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  });

  // update stored refresh token if server returned a new one
  if (response.data.refresh_token) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, response.data.refresh_token);
  }

  return response.data;
}

export async function revokeToken(token: string): Promise<void> {
  try {
    const body = new URLSearchParams({
      token,
      token_type_hint: 'refresh_token',
      client_id: KEYCLOAK_CLIENT_ID
    });
    const revokeUrl = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/revoke`;
    await axios.post(revokeUrl, body.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    });
  } catch (err) {
    // best-effort revoke; ignore errors
    // console.debug('revokeToken failed', err);
  }
}
