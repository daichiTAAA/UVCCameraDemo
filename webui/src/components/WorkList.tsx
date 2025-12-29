import { Link } from "react-router-dom";
import { formatDateTime } from "../format";
import type { WorkSummary } from "../types";

interface WorkListProps {
  works: WorkSummary[];
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
}

export function WorkList({ works, loading, error, onRetry }: WorkListProps) {
  if (loading) return <div className="inline-hint">読み込み中…</div>;

  if (error)
    return (
      <div className="card error">
        <p className="muted">{error}</p>
        {onRetry && (
          <button className="primary" onClick={onRetry}>
            再試行
          </button>
        )}
      </div>
    );

  if (!works.length)
    return <div className="inline-hint">該当する作業がありません。</div>;

  return (
    <div className="grid work-grid">
      {works.map((work) => (
        <article key={work.workId} className="card work-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">{work.workId}</p>
              <h3>
                {work.model} / {work.serial}
              </h3>
              <p className="muted">工程: {work.process}</p>
            </div>
            <span className="pill">{work.segmentCount} セグメント</span>
          </div>
          <div className="meta-row">
            <div>
              <p className="label">最初の録画</p>
              <p>{formatDateTime(work.firstRecordedAt)}</p>
            </div>
            <div>
              <p className="label">最後の録画</p>
              <p>{formatDateTime(work.lastRecordedAt)}</p>
            </div>
          </div>
          <div className="actions spread">
            <Link
              className="primary"
              to={`/works/${encodeURIComponent(work.workId)}`}
            >
              詳細を見る
            </Link>
            <span className="muted">
              latest: {formatDateTime(work.lastRecordedAt)}
            </span>
          </div>
        </article>
      ))}
    </div>
  );
}
