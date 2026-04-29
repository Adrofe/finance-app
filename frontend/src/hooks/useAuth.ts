import { useCallback, useEffect, useState, useRef } from 'react';
import axios from 'axios';

import { TOKEN_STORAGE_KEY } from '../config/env';
import { handlePkceCallback, startPkceLogin, refreshAccessToken, revokeToken } from '../services/authService';
import type { KeycloakErrorResponse, PkceCallbackResult } from '../types/auth';

export type UseAuthReturn = {
  accessToken: string;
  authLoading: boolean;
  authError: string;
  login: () => Promise<void>;
  logout: (reason?: string) => void;
  showSessionWarning: boolean;
  secondsLeft: number;
  keepSession: () => Promise<void>;
};

export function useAuth(): UseAuthReturn {
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const [accessToken, setAccessToken] = useState<string>(() => localStorage.getItem(TOKEN_STORAGE_KEY) || '');
  const refreshTimeoutRef = useRef<number | null>(null);
  const warningTimeoutRef = useRef<number | null>(null);
  const countdownIntervalRef = useRef<number | null>(null);
  const logoutTimeoutRef = useRef<number | null>(null);

  const [showSessionWarning, setShowSessionWarning] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState<number>(0);

  const REFRESH_KEY = 'finance_app_refresh_token';

  function parseJwtPayload(token: string | null) {
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const payload = JSON.parse(atob(parts[1]));
      return payload as any;
    } catch (e) {
      return null;
    }
  }

  function clearRefreshTimer() {
    if (refreshTimeoutRef.current) {
      window.clearTimeout(refreshTimeoutRef.current);
      refreshTimeoutRef.current = null;
    }
    if (warningTimeoutRef.current) {
      window.clearTimeout(warningTimeoutRef.current);
      warningTimeoutRef.current = null;
    }
    if (countdownIntervalRef.current) {
      window.clearInterval(countdownIntervalRef.current);
      countdownIntervalRef.current = null;
    }
    if (logoutTimeoutRef.current) {
      window.clearTimeout(logoutTimeoutRef.current);
      logoutTimeoutRef.current = null;
    }
  }

  async function attemptRefreshOrLogout() {
    const refreshToken = sessionStorage.getItem(REFRESH_KEY);
    if (!refreshToken) {
      // no refresh token -> force logout
      logout('Session expired');
      return;
    }

    try {
      const tokenResp = await refreshAccessToken(refreshToken);
      if (tokenResp && tokenResp.access_token) {
        localStorage.setItem(TOKEN_STORAGE_KEY, tokenResp.access_token);
        setAccessToken(tokenResp.access_token);
        // hide warning and schedule next refresh
        setShowSessionWarning(false);
        setSecondsLeft(0);
        if (countdownIntervalRef.current) {
          window.clearInterval(countdownIntervalRef.current);
          countdownIntervalRef.current = null;
        }
        scheduleRefresh(tokenResp.access_token);
        return;
      }
      logout('Session expired');
    } catch (err) {
      logout('Session expired');
    }
  }

  function scheduleRefresh(token: string | null) {
    clearRefreshTimer();
    const payload = parseJwtPayload(token);
    if (!payload || !payload.exp) return;
    const expiresAt = payload.exp * 1000;
    const now = Date.now();
    // show warning 60 seconds before expiry
    const warningDelay = Math.max(0, expiresAt - now - 60 * 1000);

    // schedule warning (user must click to keep session). If user does nothing, logout at expiry.
    if (warningDelay <= 0) {
      // show immediately
      setShowSessionWarning(true);
      const remain = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
      setSecondsLeft(remain);
      // start countdown
      if (countdownIntervalRef.current) window.clearInterval(countdownIntervalRef.current);
      countdownIntervalRef.current = window.setInterval(() => {
        const s = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
        setSecondsLeft(s);
      }, 1000) as unknown as number;
    } else {
      warningTimeoutRef.current = window.setTimeout(() => {
        setShowSessionWarning(true);
        const remain = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
        setSecondsLeft(remain);
        if (countdownIntervalRef.current) window.clearInterval(countdownIntervalRef.current);
        countdownIntervalRef.current = window.setInterval(() => {
          const s = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
          setSecondsLeft(s);
        }, 1000) as unknown as number;
      }, warningDelay) as unknown as number;
    }

    // schedule logout at exact expiry if user does nothing
    if (logoutTimeoutRef.current) {
      window.clearTimeout(logoutTimeoutRef.current);
      logoutTimeoutRef.current = null;
    }
    const untilExpiry = Math.max(0, expiresAt - now);
    logoutTimeoutRef.current = window.setTimeout(() => {
      // if user didn't click keepSession, force logout
      logout('Session expired');
    }, untilExpiry) as unknown as number;
  }

  // user action to keep session (trigger refresh immediately)
  const keepSession = useCallback(async () => {
    setShowSessionWarning(false);
    setSecondsLeft(0);
    if (countdownIntervalRef.current) {
      window.clearInterval(countdownIntervalRef.current);
      countdownIntervalRef.current = null;
    }
    // cancel pending logout timeout while refreshing
    if (logoutTimeoutRef.current) {
      window.clearTimeout(logoutTimeoutRef.current);
      logoutTimeoutRef.current = null;
    }
    await attemptRefreshOrLogout();
  }, []);

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
          // store refresh token if provided
          if ((result as PkceCallbackResult).refreshToken) {
            sessionStorage.setItem('finance_app_refresh_token', (result as PkceCallbackResult).refreshToken || '');
          }
          // schedule refresh
          scheduleRefresh(result.accessToken);
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
    (async () => {
      clearRefreshTimer();
      const refresh = sessionStorage.getItem('finance_app_refresh_token');
      if (refresh) {
        try {
          await revokeToken(refresh);
        } catch (e) {
          // ignore
        }
      }
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      sessionStorage.removeItem('finance_app_refresh_token');
      setAccessToken('');
      if (reason) {
        setAuthError(reason);
      }
      // force reload to clear any in-memory state
      try {
        window.location.href = '/';
      } catch (e) {
        // noop
      }
    })();
  }, []);

  // schedule refresh if token exists on mount
  useEffect(() => {
    if (accessToken) {
      scheduleRefresh(accessToken);
    }
    return () => clearRefreshTimer();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return {
    accessToken,
    authLoading,
    authError,
    login,
    logout,
    // session warning controls
    showSessionWarning,
    secondsLeft,
    keepSession
  };
}
