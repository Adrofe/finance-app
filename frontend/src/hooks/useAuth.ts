import { useCallback, useState } from 'react';
import axios from 'axios';

import { TOKEN_STORAGE_KEY } from '../config/env';
import { requestAccessToken } from '../services/authService';
import type { KeycloakErrorResponse } from '../types/auth';

type LoginResult = {
  ok: boolean;
};

export function useAuth() {
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const [accessToken, setAccessToken] = useState<string>(() => localStorage.getItem(TOKEN_STORAGE_KEY) || '');

  const login = useCallback(async (username: string, password: string): Promise<LoginResult> => {
    setAuthError('');
    setAuthLoading(true);

    try {
      const token = await requestAccessToken(username, password);
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
      setAccessToken(token);
      return { ok: true };
    } catch (err) {
      if (axios.isAxiosError<KeycloakErrorResponse>(err)) {
        const details = err.response?.data?.error_description || err.response?.data?.error;
        if (details) {
          setAuthError(`Login error: ${details}`);
          return { ok: false };
        }
      }

      setAuthError('Invalid credentials or Keycloak unavailable.');
      return { ok: false };
    } finally {
      setAuthLoading(false);
    }
  }, []);

  const logout = useCallback((reason?: string) => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setAccessToken('');
    if (reason) {
      setAuthError(reason);
    }
  }, []);

  return {
    accessToken,
    authLoading,
    authError,
    login,
    logout
  };
}
