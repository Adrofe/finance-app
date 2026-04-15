import { useState } from 'react';

type LoginFormProps = {
  loading: boolean;
  error: string;
  realm: string;
  onSubmit: (username: string, password: string) => Promise<{ ok: boolean }>;
};

export function LoginForm({ loading, error, realm, onSubmit }: LoginFormProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await onSubmit(username, password);

    if (result.ok) {
      setPassword('');
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Finance App Login</h1>
        <p>Sign in with your Keycloak user to load transactions.</p>

        <label htmlFor="username">Username</label>
        <input
          id="username"
          type="text"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          required
        />

        {error && <p className="state error">{error}</p>}

        <button className="btn" type="submit" disabled={loading}>
          {loading ? 'Signing in...' : 'Login'}
        </button>

        <small>Dev mode login uses Keycloak token endpoint ({realm} realm).</small>
      </form>
    </div>
  );
}
