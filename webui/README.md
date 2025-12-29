# Web UI (React + Vite)

LAN内で録画セグメントを検索・閲覧・ダウンロードする最小UIです。APIサーバーと同一オリジン配信を前提にしています。

## 必要なAPI
- `GET /api/works?workId&model&serial&process&from&to`
- `GET /api/works/{workId}`
- `GET /api/segments/{segmentId}/stream`
- `GET /api/segments/{segmentId}/download`

## 環境変数
- `VITE_API_BASE`: APIベースURL（例: `http://192.168.0.10:8080`）。未指定なら同一オリジンを使用。

## 開発
```bash
cd web
npm install
npm run dev
```

## ビルド
```bash
npm run build
```
生成物は `webui/dist` に出力されます。
