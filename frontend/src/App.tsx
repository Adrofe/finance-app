import { useCallback, useState } from 'react';

import { BankingSubTabs } from './components/BankingSubTabs';
import { LoginForm } from './components/LoginForm';
import { TabsNav } from './components/TabsNav';
import { TransactionsTable } from './components/TransactionsTable';
import { KEYCLOAK_REALM } from './config/env';
import { useAuth } from './hooks/useAuth';
import { useTransactions } from './hooks/useTransactions';
import type { AppTab, BankingSubTab, Transaction } from './types/banking';
import { AccountsTable } from './components/AccountsTable';

function toTimestamp(input?: string): number {
  if (!input) {
    return 0;
  }

  const date = new Date(input);
  return Number.isNaN(date.getTime()) ? 0 : date.getTime();
}

function formatMoney(amount: number): string {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: 'EUR'
  }).format(amount);
}

function formatDate(input?: string): string {
  if (!input) {
    return '-';
  }

  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }

  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(date);
}

function buildRecentTransactions(items: Transaction[]): Transaction[] {
  return [...items]
    .sort((a, b) => toTimestamp(b.bookingDate) - toTimestamp(a.bookingDate))
    .slice(0, 5);
}

function App() {
  const { accessToken, authLoading, authError, login, logout } = useAuth();
  const handleUnauthorized = useCallback((message: string) => {
    logout(message);
  }, [logout]);

  const { items, loading, error } = useTransactions(accessToken, handleUnauthorized);
  const [activeTab, setActiveTab] = useState<AppTab>('banking');
  const [bankingSubTab, setBankingSubTab] = useState<BankingSubTab>('dashboard');

  const totalFlow = items.reduce((sum, transaction) => sum + (transaction.amount ?? 0), 0);
  const income = items.reduce((sum, transaction) => sum + ((transaction.amount ?? 0) > 0 ? transaction.amount ?? 0 : 0), 0);
  const expenses = items.reduce((sum, transaction) => sum + ((transaction.amount ?? 0) < 0 ? Math.abs(transaction.amount ?? 0) : 0), 0);
  const recentTransactions = buildRecentTransactions(items);

  if (!accessToken) {
    return <LoginForm loading={authLoading} error={authError} realm={KEYCLOAK_REALM} onSubmit={login} />;
  }

  return (
    <div className="page">
      <header className="header">
        <h1>Finance App</h1>
        <p>Control center for your personal finances</p>
        <button className="btn secondary" onClick={() => logout('Logged out successfully.')} type="button">
          Logout
        </button>
      </header>

      <TabsNav activeTab={activeTab} onSelectTab={setActiveTab} />

      {activeTab === 'banking' && (
        <section className="panel" aria-label="Banking tab">
          <div className="section-header">
            <h2>Banking</h2>
            <p>Review balance, accounts, transactions and tags in one place.</p>
          </div>

          <BankingSubTabs activeTab={bankingSubTab} onSelectTab={setBankingSubTab} />

          {bankingSubTab === 'dashboard' && (
            <div className="dashboard-grid">
              {loading && <p className="state">Loading dashboard...</p>}
              {!loading && error && <p className="state error">{error}</p>}

              {!loading && !error && (
                <>
                  <article className="metric-card">
                    <h3>Total flow</h3>
                    <strong>{formatMoney(totalFlow)}</strong>
                    <span>Sum of all loaded transactions.</span>
                  </article>

                  <article className="metric-card">
                    <h3>Income</h3>
                    <strong>{formatMoney(income)}</strong>
                    <span>Positive movements registered.</span>
                  </article>

                  <article className="metric-card">
                    <h3>Expenses</h3>
                    <strong>{formatMoney(expenses)}</strong>
                    <span>Outgoing movements registered.</span>
                  </article>

                  <article className="sheet recent-card">
                    <div className="sheet-header">
                      <h3>Latest transactions</h3>
                      <span>{recentTransactions.length} items</span>
                    </div>
                    <ul className="recent-list">
                      {recentTransactions.map((transaction, index) => (
                        <li key={transaction.id ?? transaction.externalId ?? index}>
                          <div>
                            <strong>{transaction.merchantName || 'Unknown merchant'}</strong>
                            <small>{formatDate(transaction.bookingDate)}</small>
                          </div>
                          <strong>{formatMoney(transaction.amount ?? 0)}</strong>
                        </li>
                      ))}
                    </ul>
                  </article>
                </>
              )}
            </div>
          )}

          {bankingSubTab === 'accounts' && (
            <article className="sheet">
              <h3>Accounts</h3>
              <AccountsTable token={accessToken} />
            </article>
          )}

          {bankingSubTab === 'transactions' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Transactions</h3>
                <span>{items.length} total</span>
              </div>
              {loading && <p className="state">Loading transactions...</p>}
              {!loading && error && <p className="state error">{error}</p>}
              {!loading && !error && <TransactionsTable items={items} />}
            </article>
          )}

          {bankingSubTab === 'tags' && (
            <article className="sheet">
              <h3>Tags</h3>
              <p className="state">Tags management panel will be added in the next commit.</p>
            </article>
          )}

          {bankingSubTab === 'budgets' && (
            <article className="sheet">
              <h3>Budgets</h3>
              <p className="state">Budgets preview placeholder to plan future monthly targets.</p>
            </article>
          )}
        </section>
      )}

      {activeTab === 'insights' && (
        <section className="panel" aria-label="Insights tab">
          <div className="section-header">
            <h2>Insights</h2>
            <p>Space reserved for reports and trends.</p>
          </div>
          <article className="sheet">
            <p className="state">Insights module is ready to be implemented in upcoming commits.</p>
          </article>
        </section>
      )}
    </div>
  );
}

export default App;
