import type { InvestmentsSubTab } from '../types/investments';

type InvestmentsSubTabsProps = {
  activeTab: InvestmentsSubTab;
  onSelectTab: (tab: InvestmentsSubTab) => void;
};

const TABS: { id: InvestmentsSubTab; label: string; icon: string }[] = [
  { id: 'dashboard',   label: 'Dashboard',        icon: '📊' },
  { id: 'investments', label: 'Investments',       icon: '💼' },
  { id: 'fifo',        label: 'FIFO Operations',   icon: '🔄' },
  { id: 'catalog',     label: 'Available Assets',  icon: '🏦' },
];

export function InvestmentsSubTabs({ activeTab, onSelectTab }: InvestmentsSubTabsProps) {
  return (
    <nav className="inv-subtabs" aria-label="Investments sections">
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
