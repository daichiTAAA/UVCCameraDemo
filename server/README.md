# WebServer (ASP.NET Minimal API)

## セットアップ
- .NET SDK 8 をインストール
- プロジェクトディレクトリへ移動: `cd server`
- 依存取得: `dotnet restore`
- DB 作成＆スキーマ適用: PostgreSQL に接続し `db/schema.sql` を実行
- 実行: `dotnet run --project WebServer.csproj`
- ビルド: `dotnet build WebServer.sln`
- ビルド: `dotnet build WebServer.sln`
 appsettings.json で調整
  - `ConnectionStrings.Main`: PostgreSQL への接続文字列（必須）
  - `Storage`: `LocalRoot`(動画ルート), `IncomingSubdirectory`, `MetadataPath`
  - `Security`: `ApiKey`（全API）/`TusdHookApiKey`（tusdフック専用）
  - `Maintenance`: `RetentionDays`, `AllowDeleteUnarchived`, `BackgroundIntervalMinutes`
  - `StaticFiles.RootPath`: ビルド済み UI を同一オリジン配信するルート（例: `../web/dist`）
  - `Security`: `ApiKey`（全API）/`TusdHookApiKey`（tusdフック専用）
 `POST /api/jobs/cleanup` （保持期限削除 / バックグラウンドでも定期実行）
 `POST /api/jobs/archive` （ADLSアーカイブ: 現状スタブ）
- `POST /api/tusd/hooks/completed` （tusd完了フック）
 メタデータ: PostgreSQL (`ConnectionStrings.Main`)
 動画: ローカルFS（`Storage.LocalRoot`）に workId 配下で保存
 将来: ADLS アダプタ差し替え予定
## ストレージ実装（現状）
 `X-Api-Key` を設定すると全API/フックで必須
 tusd フックは `X-Api-Key: {Security.TusdHookApiKey}` を利用可
 `server/.gitignore` で bin/obj/.vs を除外
 バックグラウンド保持削除/アーカイブは `Maintenance.BackgroundIntervalMinutes` ごとに実行
 UI を同一オリジンで配信する場合は `web` をビルドし、`StaticFiles.RootPath` で指す

## 開発メモ
- `X-Api-Key` を設定すると全API/フックで必須
- tusd フックは `X-Api-Key: {Security.TusdHookApiKey}` を利用可
- `server/.gitignore` で bin/obj/.vs を除外
