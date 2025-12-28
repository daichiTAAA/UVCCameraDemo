# AndroidUVCCamera（AUSBC / libausbc）Compose 実装ガイド（このプロジェクト向け）

このプロジェクト（`UVCCameraDemo`）でUVC対応USBカメラを扱う際の「使い方 + 実装メモ」を統合したドキュメントです。`MainActivity.kt` の `UvcScreen` 実装を前提にしています。

> 参照: `docs/AndroidUVCCameraOriginalREADME.md`（元README / API一覧）

---

## 0. できること / 前提

- OTG対応端末にUSB（UVC）カメラを接続して、**システムのカメラ権限なし**でプレビューできます
- ただし **USBデバイス利用許可（UsbManagerのpermission）** は必要です（ダイアログが出ます）

前提条件:
- 端末が **USB Host(OTG)** に対応していること
- 接続するカメラが **UVC** に対応していること
- 給電不足で落ちることがあるので、必要に応じてセルフパワーUSBハブを使用

---

## 1. 依存関係とビルド注意（このプロジェクトは導入済み）

このプロジェクトでは、次のリポジトリ/依存関係を想定します。

- `settings.gradle.kts`
  - `google()` / `mavenCentral()` / `maven("https://maven.aliyun.com/repository/public")` / `maven("https://jitpack.io")`
- `gradle/libs.versions.toml`
  - `libausbc = "3.2.7"`
- `app/build.gradle.kts`
  - `implementation(libs.libausbc)`
  - `implementation("com.github.jiangdongguo.AndroidUSBCamera:libuvc:<libausbc版>")`

注意点:
- `libausbc` の 3.3.3 は JitPack で取得できない場合があります
- 3.2.10 でも `libuvc` の取得が 404 になるため、動作確認できたのは 3.2.7 です
- 3.2.x 系は以下に依存するため、Maven Central だけでは解決できません
  - `com.gyf.immersionbar:immersionbar:3.0.0`
  - `com.zlc.glide:webpdecoder:1.6.4.9.0`
- `libausbc` は `libuvc` を `runtime` 依存にしているため、
  `com.serenegiant.usb.USBMonitor` 型を使う場合は `libuvc` の明示追加が必要です

---

## 2. ざっくり構成

- `CameraClient` + `CameraUvcStrategy` + `AspectRatioTextureView`
- Compose では `AndroidView` で `AspectRatioTextureView` を保持して `openCamera(view)` に渡す
- 接続済みデバイスは `cameraStrategy.getUsbDeviceList()` で取得し、`deviceId.toString()` を `switchCamera(id)` に使う

---

## 3. 権限とマニフェスト

- `android.permission.CAMERA`
  - `targetSdk >= 28` だと `CameraClient.openCamera()` 内でこの権限を必須扱いされる
  - 付与されていないとプレビューが開始されない
- `android.permission.WRITE_EXTERNAL_STORAGE`
  - `captureImage()` 内でストレージ権限チェックが走るため、保存時に必要
  - Android 10+ ではスコープストレージの影響があるので `getExternalFilesDir()` を使うのが安全
- `android.hardware.usb.host`
  - USB Host 必須なら `required="true"` を宣言

注意:
- USB attach を自動検知して起動したい場合は、`intent-filter` / `device_filter.xml` / `BroadcastReceiver` が必要になります
- 本ドキュメントでは「アプリ起動 → 接続済みデバイスを開く」ルートを基本にしています

---

## 4. USB許可 / オープンの流れ

- `CameraUvcStrategy.register()` が `USBMonitor` を登録し、USB attach/detach を監視
- attach 時に `requestCameraPermission()` が内部的に呼ばれ、USB許可ダイアログが出る
- 許可後に `onConnect` が入り、`startPreview()` が内部で呼ばれる
- `openCamera(view)` は描画用の `SurfaceTexture` をセットし、以後の preview 開始に備える

Composeでは、次のライフサイクル制御が基本です。

- `LifecycleEventObserver` で `register/unRegister` と `closeCamera` を制御
  - `ON_START`: `register()`
  - `ON_STOP`: `closeCamera()`
  - `ON_DESTROY`: `unRegister()`
- `IDeviceConnectCallBack` で接続/切断イベントを受けてUI更新

---

## 5. Composeでプレビューを表示する（AndroidView）

AUSBCは内部的に `TextureView/SurfaceView`（またはライブラリ提供の `IAspectRatio` 実装View）へ描画します。
Composeでは `AndroidView` を使って、その **Viewインスタンスを安定して保持** し、open時にそれを渡します。

ポイント:
- `AspectRatioTextureView` は `remember { ... }` で生成して使い回す
- Composableの再コンポーズで View が再生成されるとプレビューが途切れやすいので注意

---

## 6. デバイス選択と切り替え

- `getUsbDeviceList()` は UVC とフィルタ済みデバイスが対象
- 3.2.x 系では `getUsbDeviceList()` が `null` を返すことがあるので `isNullOrEmpty()` でガードする
- `switchCamera(id)` の `id` は `UsbDevice.deviceId` の `String`
- 既に同じカメラが開いている場合は切り替えが無視される

---

## 7. カメラ設定（CameraRequest）

AUSBCでは `CameraRequest` でプレビュー解像度・レンダリング方式などを指定できます。

代表例（元READMEより）:

- `setPreviewWidth(1280)` / `setPreviewHeight(720)`
- `setRenderMode(CameraRequest.RenderMode.OPENGL)`
- `setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)`（MJPEGが安定しやすいことが多い）
- `setAspectRatioShow(true)`

解像度やフォーマットが合わないと「黒画面」「数秒で停止」になりやすいので、
最初は **低めの解像度 + MJPEG** から試すのがおすすめです。

---

## 8. キャプチャ（静止画 / 動画 / 音声）

代表的なAPI（元READMEより）:

- 静止画: `captureImage(callBack, savePath)`
- 録画: `captureVideoStart(callBack, path, durationInSec)` / `captureVideoStop()`
- 音声: `captureAudioStart(callBack, path)` / `captureAudioStop()`
- 配信/エンコード取得: `addEncodeDataCallBack(callBack)`
- 解像度変更: `updateResolution(width, height)`
- デバイス取得: `getDeviceList()`

保存先について:
- Android 10+ はスコープストレージの影響があるため、
  - まずは `context.getExternalFilesDir(null)` 配下（アプリ専用領域）に保存
  - 共有ギャラリーへ出したい場合は `MediaStore` を使う

注意:
- 3.2.x 系では `ICaptureCallBack.onError` / `onComplete` の引数が nullable なので null ハンドリングしておく

---

## 9. トラブルシュート

### 黒画面になる
- `CAMERA` 権限不足
- USB許可未承認
- `TextureView/Surface` が生成される前に open している
- 解像度/フォーマットがカメラ非対応（まず 640x480 + MJPEG/YUYV を試す）
- OTG給電不足（ハブ/ケーブルを見直す）

### 何も出ない
- `register()` していない / USB attach イベントが拾えていない

### 保存されない
- 保存先ディレクトリと `WRITE_EXTERNAL_STORAGE` を確認

### 接続が不安定
- セルフパワーUSBハブを使う
- 接続ケーブルを短くする
- カメラ側が高解像度で電力/帯域を使いすぎている → 解像度を下げる

---

## 10. 次にやること（このプロジェクト向けおすすめ）

- `MainActivity` に "UVCプレビュー画面" を作る（AndroidView + USB permission + open/close）
- 動作したら `UvcCameraController` と `UvcScreen` に分割
- 必要に応じて MultiCamera（複数カメラ）へ拡張

---

## 参考

- `docs/AndroidUVCCameraOriginalREADME.md`
  - CameraRequest、capture、MultiCameraの説明・API一覧
