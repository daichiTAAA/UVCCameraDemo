# WebServer (ASP.NET Minimal API)

## セットアップ
- .NET SDK 8 をインストール
- プロジェクトディレクトリへ移動: `cd server`
- 依存取得: `dotnet restore`
- DB 作成＆スキーマ適用: PostgreSQL に接続し `db/schema.sql` を実行
- 実行: `dotnet run --project WebServer.csproj`
- ビルド: `dotnet build WebServer.sln`

### 工程マスタ（テストデータ登録）
`GET /api/processes` は工程マスタ専用テーブル `processes` から取得します。

docker compose で起動時にテスト工程を自動投入したい場合は、`.env` で `TEST_DATA_ENABLED=true` を設定してください。

例（追加/更新）:
```sql
INSERT INTO processes (process, display_order, is_active, created_at, updated_at)
VALUES ('工程A', 1, TRUE, NOW(), NOW())
ON CONFLICT (process) DO UPDATE SET
	display_order = EXCLUDED.display_order,
	is_active = EXCLUDED.is_active,
	updated_at = EXCLUDED.updated_at;
```

### Docker Compose（本番/検証）
- ルートで `.env.example` を `.env` にコピーし値を設定
- `docker compose up -d --build`
- エントリ: http://localhost:8080/ （/api /files / が同一オリジン）

## 設定 (appsettings.json)
- `ConnectionStrings.Main`: PostgreSQL への接続文字列（必須）
- `Storage`: `LocalRoot`(動画ルート), `IncomingSubdirectory`, `MetadataPath`
- `Security`: `ApiKey`（全API）/`TusdHookApiKey`（tusdフック専用）
- `Lifecycle`: `RetentionDays`, `AllowDeleteUnarchived`, `BackgroundIntervalMinutes`
- `StaticFiles.RootPath`: ビルド済み UI を同一オリジン配信するルート（例: `../webui/dist` または `/app/wwwroot`）

## 主なエンドポイント
- `GET /api/health`
- `GET /api/processes`
- `GET /api/works` （query: workId/model/serial/process/from/to）
- `GET /api/works/{workId}`
- `GET /api/segments/{segmentId}/stream` （Range対応）
- `GET /api/segments/{segmentId}/download` （Range対応, attachment）
- `POST /api/tusd/hooks/completed` （tusd完了フック）
- `POST /api/jobs/cleanup` （保持期限削除 / バックグラウンド実行あり）
- `POST /api/jobs/archive` （ADLSアーカイブ）

## ストレージ実装（現状）
- メタデータ: PostgreSQL (`ConnectionStrings.Main`)
- 動画: ローカルFS（`Storage.LocalRoot`）に workId 配下で保存
- 将来: ADLS アダプタ差し替え予定

## 開発メモ
- `X-Api-Key` を設定すると全API/フックで必須
- tusd フックは `X-Api-Key: {Security.TusdHookApiKey}` を利用可
- バックグラウンド保持削除/アーカイブは `Lifecycle.BackgroundIntervalMinutes` ごとに実行
- UI を同一オリジンで配信する場合は `web` をビルドし、`StaticFiles.RootPath` で指す（Dockerfile は自動ビルド済み）
- Compose では `compose/nginx.conf` で /api /files / を同一オリジンに集約

## OpenAPI (Swagger) 生成
OpenAPI JSON は Development の Swagger から取得して `docs/openapi/` に保存します。

- 生成: `./scripts/generate_openapi_webserver.sh`
- 出力: `docs/openapi/webserver.swagger.json`

補足:
- 生成時は `OPENAPI_EXPORT=true` を有効にして、DB/バックグラウンド依存を避けています（OpenAPI生成専用）。
- `/files/*`（tusdアップロード）は ASP.NET ではなく reverse-proxy 経由で tusd が提供するため、Swagger自動生成だけでは出ません。必要な場合は `docs/openapi/overlays/tusd.paths.json` を生成物に注入しています。
