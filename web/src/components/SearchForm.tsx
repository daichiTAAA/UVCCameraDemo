import type { ChangeEvent, FormEvent } from "react";
import { useState } from "react";
import type { SearchFilters } from "../types";

interface SearchFormProps {
  initialFilters: SearchFilters;
  onSearch: (filters: SearchFilters) => Promise<void> | void;
  loading?: boolean;
}

export function SearchForm({
  initialFilters,
  onSearch,
  loading,
}: SearchFormProps) {
  const [filters, setFilters] = useState<SearchFilters>(initialFilters);

  const update =
    (key: keyof SearchFilters) => (event: ChangeEvent<HTMLInputElement>) => {
      setFilters((prev) => ({ ...prev, [key]: event.target.value }));
    };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    await onSearch(filters);
  };

  const handleReset = async () => {
    setFilters(initialFilters);
    await onSearch(initialFilters);
  };

  return (
    <form className="card search-card" onSubmit={handleSubmit}>
      <div className="card-head">
        <div>
          <h2>作業動画を探す</h2>
        </div>
        <div className="actions">
          <button
            type="button"
            className="ghost"
            onClick={handleReset}
            disabled={loading}
          >
            条件クリア
          </button>
          <button type="submit" className="primary" disabled={loading}>
            {loading ? "検索中…" : "検索"}
          </button>
        </div>
      </div>

      <div className="grid fields">
        <label className="field">
          <span>型式 (model)</span>
          <input
            name="model"
            placeholder="部分一致可"
            value={filters.model ?? ""}
            onChange={update("model")}
          />
        </label>
        <label className="field">
          <span>機番 (serial)</span>
          <input
            name="serial"
            placeholder="部分一致可"
            value={filters.serial ?? ""}
            onChange={update("serial")}
          />
        </label>
        <label className="field">
          <span>工程 (process)</span>
          <input
            name="process"
            placeholder="部分一致可"
            value={filters.process ?? ""}
            onChange={update("process")}
          />
        </label>
        <label className="field">
          <span>録画日 (from)</span>
          <input
            type="date"
            name="from"
            value={filters.from ?? ""}
            onChange={update("from")}
          />
        </label>
        <label className="field">
          <span>録画日 (to)</span>
          <input
            type="date"
            name="to"
            value={filters.to ?? ""}
            onChange={update("to")}
          />
        </label>
      </div>
    </form>
  );
}
