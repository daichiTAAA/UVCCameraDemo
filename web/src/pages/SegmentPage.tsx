import {
  Link,
  useLocation,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { getWork, segmentDownloadUrl, segmentStreamUrl } from "../api";
import { formatBytes, formatDateTime } from "../format";
import type { SegmentSummary } from "../types";

function SegmentPage() {
  const { segmentId } = useParams();
  const [searchParams] = useSearchParams();
  const workIdFromQuery = searchParams.get("workId") ?? undefined;
  const location = useLocation();
  const stateSegment = (location.state as { segment?: SegmentSummary } | null)
    ?.segment;

  const [segment, setSegment] = useState<SegmentSummary | null>(
    stateSegment ?? null
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [playbackError, setPlaybackError] = useState<string | null>(null);

  const streamUrl = useMemo(
    () => (segmentId ? segmentStreamUrl(segmentId) : ""),
    [segmentId]
  );
  const downloadUrl = useMemo(
    () => (segmentId ? segmentDownloadUrl(segmentId) : ""),
    [segmentId]
  );

  useEffect(() => {
    setPlaybackError(null);
  }, [segmentId]);

  useEffect(() => {
    const fetchSegment = async () => {
      if (!segmentId || !workIdFromQuery || segment) return;
      setLoading(true);
      setError(null);
      try {
        const work = await getWork(workIdFromQuery);
        const found = work.segments.find(
          (item) => item.segmentId === segmentId
        );
        if (found) setSegment(found);
        else setError("セグメントが見つかりません");
      } catch (err) {
        setError(err instanceof Error ? err.message : "取得に失敗しました");
      } finally {
        setLoading(false);
      }
    };

    fetchSegment();
  }, [segmentId, workIdFromQuery, segment]);

  if (!segmentId)
    return (
      <div className="page">
        <div className="card error">
          <p>segmentId が指定されていません。</p>
          <Link className="ghost" to="/">
            一覧へ戻る
          </Link>
        </div>
      </div>
    );

  return (
    <div className="page">
      <div className="breadcrumbs">
        <Link to="/">作業一覧</Link>
        {workIdFromQuery && (
          <>
            <span>/</span>
            <Link to={`/works/${encodeURIComponent(workIdFromQuery)}`}>
              作業詳細
            </Link>
          </>
        )}
        <span>/</span>
        <span>セグメント</span>
      </div>

      <div className="card">
        <div className="card-head">
          <div>
            <p className="eyebrow">セグメントID</p>
            <h1 className="tight">{segmentId}</h1>
            {segment && (
              <p className="muted">
                recordedAt: {formatDateTime(segment.recordedAt)}
              </p>
            )}
          </div>
          <div className="actions">
            <a className="ghost" href={downloadUrl}>
              ダウンロード
            </a>
            {workIdFromQuery && (
              <Link
                className="primary"
                to={`/works/${encodeURIComponent(workIdFromQuery)}`}
              >
                作業詳細へ戻る
              </Link>
            )}
          </div>
        </div>

        {loading && <p className="muted">メタデータを取得中…</p>}
        {error && <p className="muted">{error}</p>}

        <div className="player-card">
          <video
            key={segmentId}
            className="player"
            controls
            src={streamUrl}
            onError={() =>
              setPlaybackError(
                "再生できません。ダウンロードして視聴してください。"
              )
            }
          />
          <div className="player-meta">
            <p className="label">録画情報</p>
            <p>
              recordedAt: {segment ? formatDateTime(segment.recordedAt) : "N/A"}
            </p>
            <p>segmentIndex: {segment ? `#${segment.segmentIndex}` : "N/A"}</p>
            <p>size: {segment ? formatBytes(segment.sizeBytes) : "Unknown"}</p>
            <div className="actions">
              <a className="primary" href={downloadUrl}>
                ダウンロード
              </a>
            </div>
            {playbackError && <p className="warning">{playbackError}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}

export default SegmentPage;
