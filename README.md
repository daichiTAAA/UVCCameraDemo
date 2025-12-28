# UVCCameraDemo

## Webサーバー（ASP.NET Minimal API）
- プロジェクト: `server/`
- 実行: `dotnet run --project server/WebServer.csproj`
- 設定: `server/appsettings.json` で `Storage`（動画保存先・メタデータJSON）、`Security`（`X-Api-Key`）、`Maintenance`（保持日数）を調整
- 主なエンドポイント（すべて `X-Api-Key` が設定されている場合はヘッダー必須）
	- `GET /api/processes` 工程マスタ取得
	- `GET /api/works` 作業一覧検索（workId/model/serial/process/from/to）
	- `GET /api/works/{workId}` 作業詳細（セグメント一覧）
	- `GET /api/segments/{segmentId}/download` ダウンロード（Range対応）
	- `GET /api/segments/{segmentId}/stream` ストリーミング（Range対応）
	- `POST /api/tusd/hooks/completed` tusdフック（`segmentUuid/workId/model/serial/process/segmentIndex/recordedAt` 必須、tusd metadataから自動復元可）
	- `POST /api/jobs/cleanup` ローカル保持期限削除（`Maintenance.RetentionDays` 基準）
	- `POST /api/jobs/archive` ADLSアーカイブ（現状スタブ）