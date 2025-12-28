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
      <div className="hero">
        <div>
          <p className="eyebrow">セグメント検索</p>
          <h1>作業単位で録画を探す</h1>
          <p className="muted">
            LAN内サーバーにアップロードされた録画セグメントを workId / 型式 /
            機番 / 工程 / 日付で検索します。
          </p>
        </div>
        <div className="stat">
          <p className="label">現在の件数</p>
          <p className="stat-number">{works.length}</p>
        </div>
      </div>

      <SearchForm
        initialFilters={defaultFilters}
        onSearch={runSearch}
        loading={loading}
      />

      <div className="card list-card">
        <div className="card-head">
          <h2>作業一覧</h2>
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
