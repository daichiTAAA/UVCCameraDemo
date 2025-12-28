# WebServer (ASP.NET Minimal API)

## セットアップ
- .NET SDK 8 をインストール
- プロジェクトディレクトリへ移動: `cd server`
- 依存取得: `dotnet restore`
- DB 作成＆スキーマ適用: PostgreSQL に接続し `db/schema.sql` を実行
- 実行: `dotnet run --project WebServer.csproj`
- ビルド: `dotnet build WebServer.sln`

### Docker Compose（本番/検証）
- ルートで `.env.example` を `.env` にコピーし値を設定
- `docker compose up -d --build`
- エントリ: http://localhost:8080/ （/api /files / が同一オリジン）

## 設定 (appsettings.json)
- `ConnectionStrings.Main`: PostgreSQL への接続文字列（必須）
- `Storage`: `LocalRoot`(動画ルート), `IncomingSubdirectory`, `MetadataPath`
- `Security`: `ApiKey`（全API）/`TusdHookApiKey`（tusdフック専用）
- `Maintenance`: `RetentionDays`, `AllowDeleteUnarchived`, `BackgroundIntervalMinutes`
- `StaticFiles.RootPath`: ビルド済み UI を同一オリジン配信するルート（例: `../web/dist` または `/app/wwwroot`）

## 主なエンドポイント
- `GET /api/health`
- `GET /api/processes`
- `GET /api/works` （query: workId/model/serial/process/from/to）
- `GET /api/works/{workId}`
- `GET /api/segments/{segmentId}/stream` （Range対応）
- `GET /api/segments/{segmentId}/download` （Range対応, attachment）
- `POST /api/tusd/hooks/completed` （tusd完了フック）
- `POST /api/jobs/cleanup` （保持期限削除 / バックグラウンド実行あり）
- `POST /api/jobs/archive` （ADLSアーカイブ: 現状スタブ）

## ストレージ実装（現状）
- メタデータ: PostgreSQL (`ConnectionStrings.Main`)
- 動画: ローカルFS（`Storage.LocalRoot`）に workId 配下で保存
- 将来: ADLS アダプタ差し替え予定

## 開発メモ
- `X-Api-Key` を設定すると全API/フックで必須
- tusd フックは `X-Api-Key: {Security.TusdHookApiKey}` を利用可
- バックグラウンド保持削除/アーカイブは `Maintenance.BackgroundIntervalMinutes` ごとに実行
- UI を同一オリジンで配信する場合は `web` をビルドし、`StaticFiles.RootPath` で指す（Dockerfile は自動ビルド済み）
- Compose では `compose/nginx.conf` で /api /files / を同一オリジンに集約
