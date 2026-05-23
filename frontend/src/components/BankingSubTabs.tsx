import type { BankingSubTab } from '../types/banking';

type BankingSubTabsProps = {
  activeTab: BankingSubTab;
  onSelectTab: (tab: BankingSubTab) => void;
};

const TABS: { id: BankingSubTab; label: string; icon: string }[] = [
  { id: 'dashboard', label: 'Dashboard', icon: '📈' },
  { id: 'accounts', label: 'Accounts', icon: '🏛️' },
  { id: 'transactions', label: 'Transactions', icon: '🧾' },
  { id: 'budget', label: 'Presupuesto', icon: '💰' },
  { id: 'import', label: 'Importar', icon: '📥' },
  { id: 'merchants', label: 'Merchants', icon: '🏪' },
  { id: 'tags', label: 'Tags', icon: '🏷️' },
  { id: 'taxes', label: 'Retenciones', icon: '🧾' },
];

export function BankingSubTabs({ activeTab, onSelectTab }: BankingSubTabsProps) {
  return (
    <nav className="inv-subtabs" aria-label="Banking sections">
      {TABS.map(({ id, label, icon }) => (
        <button
          key={id}
          type="button"
          className={`inv-subtab${activeTab === id ? ' active' : ''}`}
          onClick={() => onSelectTab(id)}
        >
          <span className="inv-subtab-icon">{icon}</span>
          {label}
        </button>
      ))}
    </nav>
  );
}
