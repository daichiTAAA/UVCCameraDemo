import { Link } from "react-router-dom";
import { formatBytes, formatDateTime } from "../format";
import type { SegmentSummary } from "../types";

interface SegmentTableProps {
  workId: string;
  segments: SegmentSummary[];
}

export function SegmentTable({ workId, segments }: SegmentTableProps) {
  if (!segments.length)
    return <div className="inline-hint">セグメントがありません。</div>;

  return (
    <div className="table-wrapper">
      <table className="data-table">
        <thead>
          <tr>
            <th>recordedAt</th>
            <th>index</th>
            <th>ファイル名</th>
            <th>サイズ</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {segments.map((segment) => (
            <tr key={segment.segmentId}>
              <td>{formatDateTime(segment.recordedAt)}</td>
              <td>#{segment.segmentIndex}</td>
              <td>{segment.fileName ?? "segment"}</td>
              <td>{formatBytes(segment.sizeBytes)}</td>
              <td className="actions">
                <Link
                  className="ghost"
                  to={`/segments/${encodeURIComponent(
                    segment.segmentId
                  )}?workId=${encodeURIComponent(workId)}`}
                  state={{ segment }}
                >
                  再生/ダウンロード
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
