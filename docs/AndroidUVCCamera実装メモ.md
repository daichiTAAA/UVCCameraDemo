# AndroidUVCCamera 実装メモ（Compose / libausbc）

このプロジェクトでの実装で得た注意点をまとめます。`MainActivity.kt` の `UvcScreen` 実装を前提にしています。

## ざっくり構成

- `CameraClient` + `CameraUvcStrategy` + `AspectRatioTextureView`
- Compose では `AndroidView` で `AspectRatioTextureView` を保持して `openCamera(view)` に渡す
- 接続済みデバイスは `cameraStrategy.getUsbDeviceList()` で取得し、`deviceId.toString()` を `switchCamera(id)` に使う

## 権限とマニフェスト

- `android.permission.CAMERA`
  - `targetSdk >= 28` だと `CameraClient.openCamera()` 内でこの権限を必須扱いされる
  - 付与されていないとプレビューが開始されない
- `android.permission.WRITE_EXTERNAL_STORAGE`
  - `captureImage()` 内でストレージ権限チェックが走るため、保存時に必要
  - Android 10+ ではスコープストレージの影響があるので `getExternalFilesDir()` を使うのが安全
- `android.hardware.usb.host`
  - USB Host 必須なら `required="true"` を宣言

## オープンの流れ（ライブラリ側の挙動）

- `CameraUvcStrategy.register()` が `USBMonitor` を登録し、USB attach/detach を監視
- attach 時に `requestCameraPermission()` が内部的に呼ばれ、USB許可ダイアログが出る
- 許可後に `onConnect` が入り、`startPreview()` が内部で呼ばれる
- `openCamera(view)` は描画用の `SurfaceTexture` をセットし、以後の preview 開始に備える

## Compose 実装時のポイント

- `AspectRatioTextureView` は `remember { ... }` で生成して使い回す
  - 再コンポーズで View が再生成されるとプレビューが途切れやすい
- `LifecycleEventObserver` で `register/unRegister` と `closeCamera` を制御
  - `ON_START`: `register()`
  - `ON_STOP`: `closeCamera()`
  - `ON_DESTROY`: `unRegister()`
- `IDeviceConnectCallBack` で接続/切断イベントを受けてUI更新

## デバイス選択と切り替え

- `getUsbDeviceList()` は UVC とフィルタ済みデバイスが対象
- 3.2.x 系では `getUsbDeviceList()` が `null` を返すことがあるので `isNullOrEmpty()` でガードする
- `switchCamera(id)` の `id` は `UsbDevice.deviceId` の `String`
- 既に同じカメラが開いている場合は切り替えが無視される

## キャプチャ（静止画）

- `captureImage(callback, path)` を使用
- パスは `context.getExternalFilesDir(null)` 配下で作成するのが安全
- 失敗時は `ICaptureCallBack.onError` が呼ばれる
- 3.2.x 系では `onError` / `onComplete` の引数が nullable なので null ハンドリングしておく

## 依存バージョンの注意

- `libausbc` の 3.3.3 は JitPack で取得できない場合がある
  - さらに 3.2.10 でも `libuvc` が 404 になるため、動作確認できたのは 3.2.7
- 3.2.x 系は以下に依存するため、Maven Central だけでは解決できない
  - `com.gyf.immersionbar:immersionbar:3.0.0`（JitPack だと 401 になることがある）
  - `com.zlc.glide:webpdecoder:1.6.4.9.0`
  - `settings.gradle.kts` に `https://maven.aliyun.com/repository/public` の追加が必要
- `libausbc` は `libuvc` を `runtime` 依存にしている
  - アプリ側で `com.serenegiant.usb.USBMonitor` 型を使う場合は
    `implementation("com.github.jiangdongguo.AndroidUSBCamera:libuvc:<libausbc版>")` を明示追加する
- `IDeviceConnectCallBack` の `UsbControlBlock` は `com.serenegiant.usb.USBMonitor.UsbControlBlock`

## トラブルシュートのヒント

- 黒画面: `CAMERA` 権限不足、USB許可未承認、解像度不一致が典型
- 何も出ない: `register()` していない / USB attach イベントが拾えていない
- 保存されない: 保存先ディレクトリと `WRITE_EXTERNAL_STORAGE` を確認
