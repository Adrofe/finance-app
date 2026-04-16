import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';

import { TOKEN_STORAGE_KEY } from '../config/env';
import { handlePkceCallback, startPkceLogin } from '../services/authService';
import type { KeycloakErrorResponse } from '../types/auth';

export function useAuth() {
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const [accessToken, setAccessToken] = useState<string>(() => localStorage.getItem(TOKEN_STORAGE_KEY) || '');

  useEffect(() => {
    let mounted = true;

    const processCallback = async () => {
      setAuthLoading(true);

      try {
        const result = await handlePkceCallback();

        if (!mounted) {
          return;
        }

        if (!result.completed) {
          return;
        }

        if (result.error) {
          setAuthError(`Login error: ${result.error}`);
          return;
        }

        if (result.accessToken) {
          localStorage.setItem(TOKEN_STORAGE_KEY, result.accessToken);
          setAccessToken(result.accessToken);
        }
      } catch (err) {
        if (axios.isAxiosError<KeycloakErrorResponse>(err)) {
          const details = err.response?.data?.error_description || err.response?.data?.error;
          if (details) {
            setAuthError(`Login error: ${details}`);
            return;
          }
        }

        setAuthError('Could not complete login callback. Please try again.');
      } finally {
        if (mounted) {
          setAuthLoading(false);
        }
      }
    };

    processCallback();

    return () => {
      mounted = false;
    };
  }, []);

  const login = useCallback(async (): Promise<void> => {
    setAuthError('');
    setAuthLoading(true);

    await startPkceLogin();
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
