import { Link, useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { getWork } from "../api";
import { SegmentTable } from "../components/SegmentTable";
import { formatDateTime } from "../format";
import type { WorkDetail } from "../types";

function WorkDetailPage() {
  const { workId } = useParams();
  const [work, setWork] = useState<WorkDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    if (!workId) {
      setError("workId が指定されていません");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await getWork(workId);
      setWork(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "取得に失敗しました");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [workId]);

  if (loading) return <div className="page">読み込み中…</div>;
  if (error)
    return (
      <div className="page">
        <div className="card error">
          <p className="muted">{error}</p>
          <div className="actions">
            <Link className="ghost" to="/">
              一覧へ戻る
            </Link>
            <button className="primary" onClick={load}>
              再試行
            </button>
          </div>
        </div>
      </div>
    );

  if (!work) return null;

  return (
    <div className="page">
      <div className="breadcrumbs">
        <Link to="/">作業一覧</Link>
        <span>/</span>
        <span>{work.workId}</span>
      </div>

      <div className="card">
        <div className="card-head">
          <div>
            <p className="eyebrow">作業ID</p>
            <h1>{work.workId}</h1>
          </div>
          <span className="pill">{work.segmentCount} セグメント</span>
        </div>
        <div className="meta-row">
          <div>
            <p className="label">型式</p>
            <p>{work.model}</p>
          </div>
          <div>
            <p className="label">機番</p>
            <p>{work.serial}</p>
          </div>
          <div>
            <p className="label">工程</p>
            <p>{work.process}</p>
          </div>
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
      </div>

      <div className="card">
        <div className="card-head">
          <h2>セグメント一覧</h2>
          <p className="muted">recordedAt 昇順で表示しています。</p>
        </div>
        <SegmentTable workId={work.workId} segments={work.segments} />
      </div>
    </div>
  );
}

export default WorkDetailPage;
