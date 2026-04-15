import { useCallback, useState } from 'react';

import { LoginForm } from './components/LoginForm';
import { TabsNav } from './components/TabsNav';
import { TransactionsTable } from './components/TransactionsTable';
import { KEYCLOAK_REALM } from './config/env';
import { useAuth } from './hooks/useAuth';
import { useTransactions } from './hooks/useTransactions';
import type { AppTab } from './types/banking';

function App() {
  const { accessToken, authLoading, authError, login, logout } = useAuth();
  const handleUnauthorized = useCallback((message: string) => {
    logout(message);
  }, [logout]);

  const { items, loading, error } = useTransactions(accessToken, handleUnauthorized);
  const [activeTab, setActiveTab] = useState<AppTab>('banking');

  if (!accessToken) {
    return <LoginForm loading={authLoading} error={authError} realm={KEYCLOAK_REALM} onSubmit={login} />;
  }

  return (
    <div className="page">
      <header className="header">
        <h1>Finance App</h1>
        <p>Your personal finance workspace</p>
        <button className="btn secondary" onClick={() => logout()} type="button">
          Logout
        </button>
      </header>

      <TabsNav activeTab={activeTab} onSelectTab={setActiveTab} />

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

          {!loading && !error && <TransactionsTable items={items} />}
        </section>
      )}
    </div>
  );
}

export default App;
