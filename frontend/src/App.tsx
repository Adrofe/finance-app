import { useEffect, useState } from 'react';
import axios from 'axios';

const KEYCLOAK_BASE_URL = import.meta.env.VITE_KEYCLOAK_BASE_URL || 'http://localhost:8080';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'finance-app';
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'finance-client';
const KEYCLOAK_CLIENT_SECRET = import.meta.env.VITE_KEYCLOAK_CLIENT_SECRET || '';
const TOKEN_STORAGE_KEY = 'finance_app_access_token';

type Transaction = {
  id: number;
  bookingDate?: string;
  amount?: number;
  description?: string;
  merchantName?: string;
};

type ApiResponse<T> = {
  status: number;
  message: string;
  data: T;
};

type KeycloakTokenResponse = {
  access_token: string;
};

type KeycloakErrorResponse = {
  error?: string;
  error_description?: string;
};

type AppTab = 'banking' | 'transactions';

function App() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const [accessToken, setAccessToken] = useState<string>(() => localStorage.getItem(TOKEN_STORAGE_KEY) || '');

  const [items, setItems] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<AppTab>('banking');

  useEffect(() => {
    if (!accessToken) {
      setLoading(false);
      setItems([]);
      setError('');
      return;
    }

    setLoading(true);
    setError('');

    axios
      .get<ApiResponse<Transaction[]>>('/v1/api/transactions', {
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      })
      .then((res) => setItems(res.data.data || []))
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          setError('Session expired or invalid token. Please login again.');
          localStorage.removeItem(TOKEN_STORAGE_KEY);
          setAccessToken('');
          return;
        }

        setError('Could not load transactions. Check that backend is running on 8081.');
      })
      .finally(() => setLoading(false));
  }, [accessToken]);

  const handleLogin = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAuthError('');
    setAuthLoading(true);

    const body = new URLSearchParams({
      grant_type: 'password',
      client_id: KEYCLOAK_CLIENT_ID,
      username,
      password,
      scope: 'openid'
    });

    if (KEYCLOAK_CLIENT_SECRET) {
      body.append('client_secret', KEYCLOAK_CLIENT_SECRET);
    }

    try {
      const tokenUrl = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
      const response = await axios.post<KeycloakTokenResponse>(tokenUrl, body.toString(), {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      });

      localStorage.setItem(TOKEN_STORAGE_KEY, response.data.access_token);
      setAccessToken(response.data.access_token);
      setPassword('');
    } catch (err) {
      if (axios.isAxiosError<KeycloakErrorResponse>(err)) {
        const details = err.response?.data?.error_description || err.response?.data?.error;

        if (details) {
          setAuthError(`Login error: ${details}`);
          return;
        }
      }

      setAuthError('Invalid credentials or Keycloak unavailable.');
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setAccessToken('');
    setItems([]);
  };

  if (!accessToken) {
    return (
      <div className="auth-page">
        <form className="auth-card" onSubmit={handleLogin}>
          <h1>Finance App Login</h1>
          <p>Sign in with your Keycloak user to load transactions.</p>

          <label htmlFor="username">Username</label>
          <input
            id="username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />

          {authError && <p className="state error">{authError}</p>}

          <button className="btn" type="submit" disabled={authLoading}>
            {authLoading ? 'Signing in...' : 'Login'}
          </button>

          <small>
            Dev mode login uses Keycloak token endpoint ({KEYCLOAK_REALM} realm).
          </small>
        </form>
      </div>
    );
  }

  return (
    <div className="page">
      <header className="header">
        <h1>Finance App</h1>
        <p>Your personal finance workspace</p>
        <button className="btn secondary" onClick={handleLogout} type="button">
          Logout
        </button>
      </header>

      <nav className="tabs" aria-label="Main sections">
        <button
          type="button"
          className={`tab ${activeTab === 'banking' ? 'active' : ''}`}
          onClick={() => setActiveTab('banking')}
        >
          Banking
        </button>
        <button
          type="button"
          className={`tab ${activeTab === 'transactions' ? 'active' : ''}`}
          onClick={() => setActiveTab('transactions')}
        >
          Transactions
        </button>
      </nav>

      {activeTab === 'banking' && (
        <section className="panel" aria-label="Banking tab">
          <h2>Banking</h2>
          <p className="state">Dashboard.</p>
        </section>
      )}

      {activeTab === 'transactions' && (
        <section className="panel" aria-label="Transactions tab">
          <h2>Transactions</h2>
          {loading && <p className="state">Loading transactions...</p>}
          {!loading && error && <p className="state error">{error}</p>}

          {!loading && !error && (
            <table className="table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Date</th>
                  <th>Merchant</th>
                  <th>Description</th>
                  <th>Amount</th>
                </tr>
              </thead>
              <tbody>
                {items.map((t) => (
                  <tr key={t.id}>
                    <td>{t.id}</td>
                    <td>{t.bookingDate || '-'}</td>
                    <td>{t.merchantName || '-'}</td>
                    <td>{t.description || '-'}</td>
                    <td>{t.amount ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}

export default App;
