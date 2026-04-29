import type { AppTab } from '../types/banking';

type TabsNavProps = {
  activeTab: AppTab;
  onSelectTab: (tab: AppTab) => void;
};

export function TabsNav({ activeTab, onSelectTab }: TabsNavProps) {
  return (
    <nav className="tabs" aria-label="Main sections">
      <button
        type="button"
        className={`tab main-tab ${activeTab === 'banking' ? 'active' : ''}`}
        onClick={() => onSelectTab('banking')}
      >
        Banking
      </button>
      <button
        type="button"
        className={`tab main-tab ${activeTab === 'insights' ? 'active' : ''}`}
        onClick={() => onSelectTab('insights')}
      >
        Insights
      </button>
    </nav>
  );
}
