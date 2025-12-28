# WebServer (ASP.NET Minimal API)

## セットアップ
- .NET SDK 8 をインストール
- 依存取得: `dotnet restore`
- 実行: `dotnet run --project WebServer.csproj`
- ビルド: `dotnet build WebServer.sln`

## 設定
- appsettings.json で調整
  - `Storage`: `LocalRoot`(動画ルート), `IncomingSubdirectory`, `MetadataPath`
  - `Security`: `ApiKey`（全API）/`TusdHookApiKey`（tusdフック専用）
  - `Maintenance`: `RetentionDays`, `AllowDeleteUnarchived`

## 主なエンドポイント
- `GET /api/health`
- `GET /api/processes`
- `GET /api/works` （query: workId/model/serial/process/from/to）
- `GET /api/works/{workId}`
- `GET /api/segments/{segmentId}/stream` （Range対応）
- `GET /api/segments/{segmentId}/download` （Range対応, attachment）
- `POST /api/tusd/hooks/completed` （tusd完了フック）
- `POST /api/jobs/cleanup` （保持期限削除）
- `POST /api/jobs/archive` （ADLSアーカイブ: 現状スタブ）

## ストレージ実装（現状）
- メタデータ: JSONファイル（`Storage.MetadataPath`）
- 動画: ローカルFS（`Storage.LocalRoot`）に workId 配下で保存
- 将来: PostgreSQL/ADLS アダプタ差し替え予定

## 開発メモ
- `X-Api-Key` を設定すると全API/フックで必須
- tusd フックは `X-Api-Key: {Security.TusdHookApiKey}` を利用可
- `server/.gitignore` で bin/obj/.vs を除外
