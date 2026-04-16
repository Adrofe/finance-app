type LoginFormProps = {
  loading: boolean;
  error: string;
  realm: string;
  onSubmit: () => Promise<void>;
};

export function LoginForm({ loading, error, realm, onSubmit }: LoginFormProps) {
  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit();
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Finance App Login</h1>
        <p>Continue with Keycloak secure login (Authorization Code + PKCE).</p>

        {error && <p className="state error">{error}</p>}

        <button className="btn" type="submit" disabled={loading}>
          {loading ? 'Redirecting...' : 'Login with Keycloak'}
        </button>

        <small>Realm: {realm}</small>
      </form>
    </div>
  );
}
