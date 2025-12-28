# UVCCameraDemo

## Webサーバー（ASP.NET Minimal API）
- プロジェクト: `server/`
- 実行: `dotnet run --project server/WebServer.csproj`
- 設定: `server/appsettings.json` で `ConnectionStrings.Main`（PostgreSQL必須）, `Storage`（動画保存先）, `Security`（`X-Api-Key`）, `Maintenance`（保持日数/定期実行間隔）, `StaticFiles.RootPath`（ビルドUI配信用）を調整
- スキーマ: `server/db/schema.sql`
- 主なエンドポイント（`X-Api-Key` を設定している場合は必須）
	- `GET /api/processes` 工程マスタ取得
	- `GET /api/works` 作業一覧検索（workId/model/serial/process/from/to）
	- `GET /api/works/{workId}` 作業詳細（セグメント一覧）
	- `GET /api/segments/{segmentId}/download` ダウンロード（Range対応）
	- `GET /api/segments/{segmentId}/stream` ストリーミング（Range対応）
	- `POST /api/tusd/hooks/completed` tusdフック（メタデータ復元対応）
	- `POST /api/jobs/cleanup` ローカル保持期限削除（バックグラウンド実行あり）
	- `POST /api/jobs/archive` ADLSアーカイブ（現状スタブ）