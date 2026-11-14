import { getInvestmentTypeVisual } from '../constants/visualConfig';
import './investment-type-filter.css';

type TypeOption = { code: string; name: string; count?: number };

type Props = {
  options: TypeOption[];
  selected: string[];
  onChange: (selected: string[]) => void;
  label?: string;
};

export function toggleInvestmentType(selected: string[], code: string): string[] {
  return selected.includes(code) ? selected.filter(item => item !== code) : [...selected, code];
}

export function InvestmentTypeFilter({ options, selected, onChange, label = 'Tipo de activo' }: Props) {
  return (
    <div className="investment-type-filter" role="group" aria-label={label}>
      <span className="investment-type-filter-label">{label}</span>
      <div className="investment-type-filter-options">
        <button type="button" className={`investment-type-filter-button${selected.length === 0 ? ' active' : ''}`} aria-pressed={selected.length === 0} onClick={() => onChange([])}>Todos</button>
        {options.map(option => {
          const visual = getInvestmentTypeVisual(option.code, option.name);
          const active = selected.includes(option.code);
          return (
            <button
              key={option.code}
              type="button"
              className={`investment-type-filter-button${active ? ' active' : ''}`}
              aria-label={option.count == null ? option.name : `${option.name}, ${option.count} ${option.count === 1 ? 'activo' : 'activos'}`}
              aria-pressed={active}
              onClick={() => onChange(toggleInvestmentType(selected, option.code))}
            >
              <span aria-hidden="true">{visual.emoji}</span> {option.name}
              {option.count != null && <span className="investment-type-filter-count" aria-hidden="true">{option.count}</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}