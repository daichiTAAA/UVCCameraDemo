import { useEffect, useState } from "react";
import { searchWorks } from "../api";
import { SearchForm } from "../components/SearchForm";
import { WorkList } from "../components/WorkList";
import type { SearchFilters, WorkSummary } from "../types";

const defaultFilters: SearchFilters = {
  workId: "",
  model: "",
  serial: "",
  process: "",
  from: "",
  to: "",
};

function WorkListPage() {
  const [works, setWorks] = useState<WorkSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runSearch = async (filters: SearchFilters) => {
    setLoading(true);
    setError(null);
    try {
      const result = await searchWorks(filters);
      setWorks(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "検索に失敗しました");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    runSearch(defaultFilters);
  }, []);

  return (
    <div className="page">
      <SearchForm
        initialFilters={defaultFilters}
        onSearch={runSearch}
        loading={loading}
      />

      <div className="card list-card">
        <div className="card-head">
          <h2>作業動画一覧</h2>
          <span className="pill">{works.length} 件</span>
          {loading && <span className="pill">読み込み中</span>}
          {error && <span className="pill danger">エラー</span>}
        </div>
        <WorkList
          works={works}
          loading={loading}
          error={error}
          onRetry={() => runSearch(defaultFilters)}
        />
      </div>
    </div>
  );
}

export default WorkListPage;
