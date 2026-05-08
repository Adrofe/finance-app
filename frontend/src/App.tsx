import { useCallback, useState } from 'react';

import { BankingSubTabs } from './components/BankingSubTabs';
import { InvestmentsSubTabs } from './components/InvestmentsSubTabs';
import { InvestmentCatalogTable } from './components/InvestmentCatalogTable';
import { InvestmentsDashboard } from './components/InvestmentsDashboard';
import { InvestmentsOverviewTable } from './components/InvestmentsOverviewTable';
import { InvestmentOperationsTable } from './components/InvestmentOperationsTable';
import { LoginForm } from './components/LoginForm';
import { TabsNav } from './components/TabsNav';
import { TransactionsTable } from './components/TransactionsTable';
import { KEYCLOAK_REALM } from './config/env';
import { useAuth } from './hooks/useAuth';
import { useTransactions } from './hooks/useTransactions';
import type { AppTab, BankingSubTab } from './types/banking';
import type { InvestmentsSubTab } from './types/investments';
import { AccountsTable } from './components/AccountsTable';
import { Dashboard } from './components/Dashboard';

function App() {
  const { accessToken, authLoading, authError, login, logout, showSessionWarning, secondsLeft, keepSession } = useAuth();
  const handleUnauthorized = useCallback((message: string) => {
    logout(message);
  }, [logout]);

  const { items, loading, error, refresh } = useTransactions(accessToken, handleUnauthorized);
  const [activeTab, setActiveTab] = useState<AppTab>('banking');
  const [bankingSubTab, setBankingSubTab] = useState<BankingSubTab>('dashboard');
  const [investmentsSubTab, setInvestmentsSubTab] = useState<InvestmentsSubTab>('dashboard');

  if (!accessToken) {
    return <LoginForm loading={authLoading} error={authError} realm={KEYCLOAK_REALM} onSubmit={login} />;
  }

  return (
    <div className="page">
      {showSessionWarning && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal" style={{ width: 420 }}>
            <div className="modal-header">
              <h4>Tu sesión caduca pronto</h4>
            </div>
            <div className="modal-body" style={{ display: 'block' }}>
              <p>Tu sesión expira en <strong>{secondsLeft}</strong> segundos.</p>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button className="btn" type="button" onClick={() => logout('Sesión cerrada manualmente')}>Cerrar sesión</button>
                <button className="btn primary" type="button" onClick={() => keepSession()}>Mantener sesión</button>
              </div>
            </div>
          </div>
        </div>
      )}
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
            <p>Review balance, accounts and transactions in one place.</p>
          </div>

          <BankingSubTabs activeTab={bankingSubTab} onSelectTab={setBankingSubTab} />

          {bankingSubTab === 'dashboard' && (
            <Dashboard
              token={accessToken}
              transactions={items}
              onUnauthorized={handleUnauthorized}
            />
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
              {!loading && !error && <TransactionsTable items={items} accessToken={accessToken} onRefresh={refresh} />}
            </article>
          )}


        </section>
      )}

      {activeTab === 'investments' && (
        <section className="panel" aria-label="Investments tab">
          <div className="section-header">
            <h2>Investments</h2>
            <p>Track your portfolio, operations and available assets.</p>
          </div>

          <InvestmentsSubTabs activeTab={investmentsSubTab} onSelectTab={setInvestmentsSubTab} />

          {investmentsSubTab === 'dashboard' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Portfolio Dashboard</h3>
                <span>Resumen global, fiscal y composicion de cartera</span>
              </div>
              <InvestmentsDashboard token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}

          {investmentsSubTab === 'investments' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Investments</h3>
                <span>Resumen por instrumento y métricas de cartera</span>
              </div>
              <InvestmentsOverviewTable token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}

          {investmentsSubTab === 'fifo' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Operations</h3>
                <span>CRUD de compras y ventas con recalculo FIFO</span>
              </div>
              <InvestmentOperationsTable token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}

          {investmentsSubTab === 'catalog' && (
            <InvestmentCatalogTable token={accessToken} onUnauthorized={handleUnauthorized} />
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
