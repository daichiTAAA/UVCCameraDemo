# UVCCameraDemo

## Webサーバー（ASP.NET Minimal API）
- プロジェクト: `webserver/`
- 実行: `dotnet run --project webserver/WebServer.csproj`
- OpenAPI生成: `bash scripts/generate_openapi_webserver.sh`（出力: `docs/openapi/webserver.swagger.json`）
- 設定: `webserver/appsettings.json` で `ConnectionStrings.Main`（PostgreSQL必須）, `Storage`（動画保存先）, `Security`（`X-Api-Key`）, `Lifecycle`（保持日数/定期実行間隔）, `StaticFiles.RootPath`（ビルドUI配信用）を調整
- スキーマ: `webserver/db/schema.sql`
- 主なエンドポイント（`X-Api-Key` を設定している場合は必須）
	- `GET /api/processes` 工程マスタ取得
	- `GET /api/works` 作業一覧検索（workId/model/serial/process/from/to）
	- `GET /api/works/{workId}` 作業詳細（セグメント一覧）
	- `GET /api/segments/{segmentId}/download` ダウンロード（Range対応）
	- `GET /api/segments/{segmentId}/stream` ストリーミング（Range対応）
	- `POST /api/tusd/hooks/completed` tusdフック（メタデータ復元対応）
	- `POST /api/jobs/cleanup` ローカル保持期限削除（バックグラウンド実行あり）
	- `POST /api/jobs/archive` ADLSアーカイブ

### Docker Compose で起動
- `.env.example` を `.env` にコピーして API キーや DB パスワードを設定
- `docker compose up -d --build`
- 入口: http://localhost:8080/ （/api, /files, UI を同一オリジンで提供。nginx 設定は `compose/nginx.conf`）
	- UI: `webui`（Vite build を nginx で配信）
	- API: `webserver`（ASP.NET）