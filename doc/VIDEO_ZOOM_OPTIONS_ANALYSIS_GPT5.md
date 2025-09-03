# 影片縮放／亮度／對比 實作選項指南

本文件整理在 LiteGallery 媒體瀏覽器 App 中，實作影片「縮放、亮度、對比」的可選方案、取捨與建議，並對既有程式現況做出說明。

## 結論摘要
- 縮放：以 `TextureView.setTransform(Matrix)` 實作最輕量、互動流暢、耗電較低，適合媒體「瀏覽」情境。
- 亮度／對比：若需要「針對影片本身」精準調整，使用 Media3 Effects；僅全螢幕變亮可用 `Window.screenBrightness`，僅變暗可用半透明遮罩（scrim）。
- 系統層控制（Window 亮度、ColorFilter）：無法精準針對影片內容或對比，且對 `SurfaceView` 多半無效，不建議作為長期方案。

## 目前 App 的實作
- 影片縮放採用 `TextureView.setTransform(Matrix)`：
  - `item_media_viewer.xml` 指定 `PlayerView` 使用 TextureView 表面：
    ```xml
    <com.litegallery.ZoomablePlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="false"
        app:resize_mode="fit"
        app:surface_type="texture_view" />
    ```
  - `ZoomablePlayerView.kt` 內部尋找 `TextureView` 並呼叫 `TextureView.setTransform(transformMatrix)` 進行捏合縮放與平移。
  - 僅在偵測到 `SurfaceView` 才 fallback 用 `scaleX/scaleY + translation`（本案因 XML 指定 `texture_view`，實際不會走到）。
- 未使用 Media3 Effects：目前專案未搜尋到 `androidx.media3.effect`、`setVideoEffects` 等 API。
- 照片：以 `ZoomImageView`（ScaleType.MATRIX + Matrix）實作縮放。

## 各選項比較

### TextureView.setTransform(Matrix)
- 範圍：只影響影片紋理（內容），不影響疊在上方的 UI/字幕等子 View。
- 畫質：對解碼後的影片紋理做 GPU 取樣縮放；未超過「原生解析度在螢幕上的 1:1」前不會明顯柔化，超過則屬上採樣會有雙線性柔化（但非馬賽克塊狀）。
- 耗電：低於 Media3 Effects。僅多一次紋理合成取樣，成本低。
- 難度：低；以 Matrix 同時處理縮放、平移、旋轉等。
- 適用：媒體瀏覽器的即時捏合縮放、雙擊縮放。

### Media3 Effects（androidx.media3.effect）
- 範圍：在 GL 管線中對「影片內容本身」做幾何與色彩處理；播放與匯出（Transformer）可共用效果鏈，一致性高。
- 能力：亮度、對比、飽和、LUT、裁切、縮放、平移等皆可組合。
- 畫質：與 `TextureView` 做「純縮放」本質相近（紋理取樣）；優勢在能做色彩與其他特效。
- 耗電：高於 `TextureView.setTransform`。通常會引入額外的離屏渲染（FBO）與多重 pass，增加 GPU/記憶體頻寬。
- 適用：需要 per-video 亮度/對比、或要「導出結果與預覽一致」的場景。

### view.scaleX/scaleY（視圖層縮放）
- 範圍：縮放的是整個 View，包括控制列、字幕等；若套在 `PlayerView` 會連 UI 一起被放大。
- 事件與版面：會影響命中區域與版面裁切（父容器 `clipChildren` 等）。
- 畫質與效能：與 `setTransform` 對影片內容的取樣成本相近，但常引發 UI 不必要的重繪，不利「只放大影片」。
- 適用：僅在你確定要整個 `PlayerView`（含疊層）一起縮放時。

### 系統層／視圖層的其他方式
- `WindowManager.LayoutParams.screenBrightness`：調整整個視窗的螢幕亮度；最省電，但無法改對比，也會影響 UI，全局生效。
- 黑色半透明遮罩（scrim overlay）：在影片上方加一層黑色遮罩以「變暗」；極低成本，適合瀏覽器的快速降亮需求；無法「變亮」。
- ColorFilter：對 `SurfaceView/TextureView` 多半無效或不可控；不建議用於影片色彩調整。

## 常見疑問（FAQ）

- `TextureView.setTransform` 會不會把「已渲染的低解析度」再拉伸？
  - 不會。該方法改變的是解碼後影片紋理到螢幕的映射矩陣，由 GPU 即時取樣縮放，非對截圖或縮圖再放大。

- `setTransform` 與 `view.scaleX/scaleY` 有什麼不同？
  - `setTransform` 只影響影片內容映射，不動 UI 疊層；`scaleX/scaleY` 會把整個 View（含控制列/字幕）一起放大，並影響事件命中與版面。

- 哪個比較省電？
  - 只做縮放時：`TextureView.setTransform` 較省電。
  - 需要色彩特效（亮度/對比等）時：Media3 Effects 功耗較高。

## HDR 與功耗考量
- `SurfaceView` 可走硬體覆蓋與平台 HDR，畫質與功耗最佳；但 `SurfaceView` 無法用矩陣做互動縮放（不支援 `setTransform`）。
- `TextureView` 與 Effects 都走 GPU 合成，可能關閉某些裝置的零拷貝/HDR 路徑；為互動縮放的便利性做取捨。
- 建議策略：
  - 預設使用 `SurfaceView` 以保留 HDR 與省電；進入「縮放模式」時切換到 `TextureView`（需要在 UI 層做 PlayerView/Surface 切換策略）。
  - 若維持現行 `TextureView`，已能達成良好互動縮放；僅在必要時啟用 Effects。

## 畫質最佳化建議（縮放）
- 限制最大縮放倍率（常見上限 2–3 倍，依素材而定）。
- 提供「1:1 像素」模式：允許以黑邊方式維持原生清晰度。
- 確認底層紋理未被中間降解析度（一般 `PlayerView` 會正確配置；若自行管理 `SurfaceTexture`，需設定 `setDefaultBufferSize`）。

## 實作要點與範例

### 以 TextureView + Matrix 實作（現行做法）
- 取得 `TextureView` 後設定矩陣：
  ```kotlin
  val transform = Matrix()
  // 以手勢焦點縮放
  transform.postScale(scaleFactor, scaleFactor, focusX, focusY)
  // 平移
  transform.postTranslate(dx, dy)
  (videoSurface as TextureView).setTransform(transform)
  videoSurface.invalidate()
  ```
- 為避免放大後出框，需計算並限制可平移範圍（本專案的 `ZoomablePlayerView` 已實作 `constrainTransform()` 邏輯）。

### 以 Media3 Effects 實作（選用）
- 依賴：`androidx.media3:media3-exoplayer`、`media3-ui`、`media3-effect`（建議 1.3.x 以上）。
- 播放套用：使用 `player.setVideoEffects(listOf(...))`（或等價 API）組合幾何與色彩效果。
- 亮度/對比：使用內建色彩效果或自訂 `GlEffect` 著色器。
- 效能：盡量合併為單一 GL pass、在用戶調整期間才啟用，避免常駐。

## 瀏覽器情境的推薦策略
- 預設：
  - 影片縮放用 `TextureView.setTransform`（互動流暢、耗電較低）。
  - 不做特效時保留 `SurfaceView` 更省電（若要支援「縮放模式」可切換到 `TextureView`）。
- 亮度：
  - 需要全螢幕變亮：`Window.screenBrightness` 最省電但會影響 UI。
  - 僅需變暗：在影片上方放黑色半透明 scrim。
  - 需要 per-video 變亮/對比：以 Media3 Effects 做成「選用」開關或只在調整期間啟用。

## 專案現況摘要（快速核對）
- 縮放：`ZoomablePlayerView` 以 `TextureView.setTransform(Matrix)` 實作。
- 版面：`item_media_viewer.xml` 已設定 `app:surface_type="texture_view"`。
- Effects：目前未整合 Media3 Effects。
- 照片：`ZoomImageView` 以 Matrix 實作縮放。

---

若後續需要，我們可以：
- 加入「縮放模式」切換（SurfaceView ↔ TextureView），兼顧 HDR/功耗與互動縮放需求。
- 提供亮度/對比的 Media3 Effects 選項，並與 UI 亮度／scrim 模式協同運作。

---

# Raw data

user
這個相簿 App 目前使用 Media3 作為 vide player，我想要實作影片 zoom/brightness/
contrast 功能，請問應該使用 Media3 Effect 還是系統層面控制(例如Window屬性、
ColorFilter)？


codex
結論

- 建議用 Media3 effect 管線處理「亮度/對比」，並視需求選擇用「Media3 變換」或「TextureView 視圖矩陣」做「縮放」。
- 避免只用系統層級控制（如 Window 亮度或 View 的 ColorFilter），因為無法精準針對影片本身、也無法做對比，且在
SurfaceView 上多半無效。
為什麼選 Media3 Effects
- 精準度: 針對影片畫面本身調整，非整個 Activity/UI。
- 能力齊全: 亮度、對比、飽和、LUT、裁切/縮放/平移等都在 androidx.media3.effect。
- 一致性: 播放時與匯出（Transformer）能用同一組效果，預覽與成品一致。
- 可組合: 可同時做縮放、裁切與色彩調整，不用混搭多個系統層方法。                                                         什麼時候用系統層/視圖層
- 視圖層縮放（簡單、低成本）: 只要做「捏合縮放/平移」且不需要導出影片，使用 TextureView.setTransform(Matrix) 成本最 低、延遲小。前提是 PlayerView 使用 TextureView（非預設 SurfaceView）。
- 系統層亮度: WindowManager.LayoutParams.screenBrightness 只能調整整個視窗亮度、無法改對比且會影響 UI；僅適合臨時全 螢幕變亮/變暗的需求。
- ColorFilter: 對 SurfaceView/PlayerView 基本無效；不建議作為影片色彩調整方案。                                          實作建議  - 依賴: 加上 androidx.media3:media3-exoplayer、androidx.media3:media3-ui、androidx.media3:media3-effect（建議使用   1.3.x 以上）。
- 播放時套用效果:
- 使用 player.setVideoEffects(...)（或版本上的等價 API）傳入 androidx.media3.effect 的效果清單。
- 亮度/對比: 使用 color 調整類效果（例如提供亮度/對比參數的效果類別或自訂 GlEffect 著色器）。
- 縮放/平移: 使用幾何變換類（例如 MatrixTransformation/Crop 等）做可控的 zoom/translate。
- 動態調整: 需即時改變參數（手勢/滑桿）時，偏好使用能讀取外部狀態的效果或重新設定 VideoEffects（避免重建 Player）。 若只做縮放且追求極致流暢，改用 TextureView.setTransform。
- SurfaceView 注意: 若要用視圖矩陣縮放，將 PlayerView 切到 TextureView 類型；否則用 Media3 的幾何效果在 GL 管線內   處理。
- 效能: GL 效果會增加 GPU 工作量（特別是 4K/HDR）。建議提供開關、做參數節流，並在低階裝置上 fallback 為視圖層縮放或 僅亮度。  推薦策略  - 需要亮度/對比（或未來要匯出/截圖一致）: 走 Media3 effect 全套（色彩 + 幾何）。
- 只要「播放時的捏合縮放」且追求最低延遲/最低風險: TextureView.setTransform 做縮放；色彩不調整或僅用最小限度的      Media3 色彩效果。
- 不建議: 依賴 Window 亮度或 ColorFilter 當作影片級的色彩調整。                                                          如果你願意，我可以看你專案中的 Player/PlayerView 初始化位置，幫你放入 media3-effect 的實作骨架（或改成 TextureView  以支援視圖矩陣縮放）。                                                                                              
user
對於一個媒體瀏覽器而非影片編輯的 App 來說，系統層面的輕量實作是不是更合適且效能更佳？                                    
codex
Short Answer
- For a media browser, prefer view/system-level for zoom; use Media3 Effects only if you truly need per-video       brightness/contrast. This keeps battery/HDR quality better and complexity lower.                                         Zoom      - TextureView transform: best fit for browsers. Use PlayerView with TextureView and apply                           TextureView.setTransform(Matrix) for pinch zoom/pan. Very low overhead and simple.
- SurfaceView note: default PlayerView uses SurfaceView (great for HDR/battery) but can’t be pinch-zoomed. Switch toTextureView only when zoom is needed, or provide a “Zoom mode” toggle.                                                   Brightness- Whole-screen: use WindowManager.LayoutParams.screenBrightness for true luminance change (best for battery; affectsentire UI).
- Per-video darken only: overlay a black scrim view above the video; trivial and cheap, works even with SurfaceView (but cannot brighten beyond current screen level).
- Per-video brighten: requires Media3 color effect; system/UI options can’t increase video brightness selectively.       Contrast  - Needs Media3. View/system-level hacks (ColorFilter) don’t reliably affect SurfaceView/TextureView content and     won’t give proper contrast.                                                                                         
- Keep SurfaceView when possible to preserve HDR pipeline and reduce power. TextureView and GL/effect paths often   disable platform HDR and add an extra GPU pass.
- For a browser, default to no effects; enable them only on-demand.

Recommended Strategy

- Default: SurfaceView, no effects (best quality/efficiency).
- Zoom needed: temporarily switch to TextureView and use view transform.
- Brightness quick control: window brightness; for darken-only, scrim overlay.
- Precision brightness/contrast (per-video): opt-in Media3 Effects, used only while the user adjusts.

If you want, I can check how your PlayerView is set up and add:

- A “Zoom mode” that switches to TextureView and pinch-to-zoom.
- A dimming scrim for quick brightness down.
- Optional Media3 color effects behind a toggle for per-video brightness/contrast.

user
你提到的 TextureView.setTransform(Matrix)，其原理是不是將已經渲染好的低解析度圖像拉伸？可能會導致畫面模糊或像素
化嗎？

codex
重點結論
     - 不是把「已渲染的低解析度圖片」再拉伸。TextureView.setTransform(Matrix)是對解碼後的影片紋理做視圖層的幾何變換，GPU 以單次取樣進行縮放，並非對截圖或縮圖再放大。
- 會不會模糊取決於是否「超過原生解析度在螢幕上的1:1對應」；放大超過此點就屬於上採樣，會有雙線性過濾帶來的柔化（非明 顯像素鋸齒）。                                                                                                           運作原理（簡述）
- 解碼器把每一帧輸出到 SurfaceTexture（通常是影片原生解析度）；TextureView把這個紋理交給合成器。
- setTransform(Matrix)只改變紋理→螢幕的映射矩陣，不改變解碼解析度或中間再壓縮。
- 縮放由 GPU 完成，預設使用雙線性過濾：上採樣看起來會「變柔」，但不會出現明顯的馬賽克像素塊（除非極端放大或素材本身 就有壓縮方塊）。                                                                                                    
- 純「縮放/平移」：TextureView 變換與 Media3 GL 效果在畫質上本質相近（同樣是紋理取樣）。沒有「Media3 比 TextureView 更銳利」的天生優勢。
- Media3 的價值在於可同時做色彩/裁切/組合特效與可導出一致結果；若只要互動縮放，TextureView 更簡單且同樣清晰。       
如何避免畫面變得過於柔軟

- 控制最大縮放倍率：依素材與螢幕像素比設上限（常見 2–3 倍）。例如 4K→FHD 留有放大空間；720p 在 FHD 上本就被放大，額 外縮放會較快變柔。
- 提供「1:1 像素」模式：在不必填滿螢幕時，允許以黑邊方式維持原生清晰度。
- 確認底層紋理不是被降解析度：一般 ExoPlayer/PlayerView 已用影片原生解析度供紋理；若自行管理 SurfaceTexture，在收到 onVideoSizeChanged 時可設 setDefaultBufferSize(videoWidth, videoHeight) 以避免不必要的中間縮放。

實務建議

- 媒體瀏覽器只做捏合縮放：選 TextureView.setTransform，畫質足夠且簡單、低延遲。
- 若要亮度/對比等色彩調整：再用 Media3 Effects 針對影片本體處理。
- 追求 HDR/功耗：平時用 SurfaceView；進入「縮放模式」再切到 TextureView。

user
所以TextureView.setTransform 與 view.scaleX/view.scaleY 實現縮放兩者不一樣是嗎？

codex
差異與建議
     - 作用範圍: TextureView.setTransform(Matrix)只影響「影片紋理內容」的映射；view.scaleX/scaleY是對「整個 View 元件」做
縮放。
- UI 影響: 用 setTransform 只會放大影片，不會放大控制列/字幕等疊在上方的子 View；scaleX/scaleY若套在 PlayerView 會連
UI 一起放大。
- 版面/裁切: setTransform 不改變 View 尺寸與位置，內容超出會在 View 邊界被裁切；scaleX/scaleY讓 View 視覺上變大，可
能被父容器 clipChildren 裁掉或蓋到旁邊項目。
- 手勢/控制: setTransform 可用單一矩陣同時做「以手勢焦點縮放+平移」，數學更直覺；scaleX/scaleY常還要配 translationX/
Y 才能達到相同行為。
- 畫質: 兩者本質上都是對解碼後紋理做 GPU 縮放，畫質相當；不存在 setTransform 比 scaleX/scaleY更清晰的先天差異。
- 效能: 都是低成本的 GPU 變換；setTransform只動到影片紋理，避免不必要地重繪 UI/疊層，通常更合適做「只縮放影片」。
- HDR/相容性: 真正能被縮放的前提是使用 TextureView。對 SurfaceView，scaleX/scaleY與 setTransform都不適用
（setTransform只屬於 TextureView），因此要互動縮放需切到 TextureView。
- 精細控制: setTransform可用任意 3x3 矩陣（非等比、旋轉、平移都行）；scaleX/scaleY僅等比/單軸縮放，需結合其他屬性才
可達成同效果。
- 事件影響: scaleX/scaleY會影響該 View 的點擊/命中區域與轉換矩陣；setTransform只改內容映射，手勢區域不變（通常在外層
    - Matrix m = new Matrix();
    - m.postScale(scale, scale, focusX, focusY);
需要的話，我可以幫你在現有 PlayerView 切換到 TextureView 並加上捏合縮放/平移的矩陣管理骨架，保持字幕與控制列不被
拉伸。

>_
  ✓ 📂 ls -la
  ✓ 📂 app
✔ You approved codex to run grep -RIn --exclude-dir=.git -E "TextureView|setTransform\(|scaleX|scaleY|
Matrix\(|PlayerView|androidx.media3|VideoEffects|setVideoEffects|Effect|GlEffect|ColorFilter|screenBrightness|
ScaleGestureDetector|zoom" "LiteGallery/app/src" | sed -n '1,200p' every time this session
  ✓ 🔎 TextureView|setTransform\(|scaleX|scaleY|Matrix\(|PlayerView|androidx.media3|VideoEffects|setVideoEffects|
Effect|GlEffect|ColorFilter|screenBrightness|ScaleGestureDetector|zoom in app
  ✓ 📖 ZoomablePlayerView.kt
  ✓ 📖 ZoomablePlayerView.kt
  ✓ 📖 item_media_viewer.xml
  ✓ 📖 ZoomImageView.kt
  ✓ 📖 ZoomImageView.kt
  ✓ 📖 MediaViewerAdapter.kt
  ✓ 📖 MediaViewerAdapter.kt
  ✓ 📖 MediaViewerActivity.kt
  ✓ 📖 MediaViewerActivity.kt

✔ You approved codex to run grep -RIn --exclude-dir=.git -E "androidx\.media3\.effect|setVideoEffects|VideoEffects|
GlEffect|GlMatrixTransformation|ColorFilter|Brightness|Contrast|Transformer" "LiteGallery/app/src/main" | sed -n
'1,200p' every time this session
  ✓ 🔎 androidx\.media3\.effect|setVideoEffects|VideoEffects|GlEffect|GlMatrixTransformation|ColorFilter|Brightness|
Contrast|Transformer in main
  ✓ 📖 VideoViewHolder.kt

codex
結論      - 影片縮放使用 TextureView.setTransform(Matrix)，由自訂 ZoomablePlayerView 實作；非 Media3 Effects，也不是單純      view.scaleX/scaleY。                                                                                                     依據      - LiteGallery/app/src/main/res/layout/item_media_viewer.xml:
- com.litegallery.ZoomablePlayerView 並設 app:surface_type="texture_view"（確保用 TextureView）。
- LiteGallery/app/src/main/java/com/litegallery/ZoomablePlayerView.kt:
- 尋找內部視圖後對 TextureView 執行 surface.setTransform(transformMatrix)。
- 僅在是 SurfaceView 時才 fallback 用 scaleX/scaleY + translation（此案因 XML 指定 TextureView，實際走不到）。      - 透過 onVideoSizeChanged 傳入影片尺寸，計算基準縮放與平移。
- 未發現 androidx.media3.effect、setVideoEffects 等 Media3 Effects 相關使用。
- 照片使用 ZoomImageView 並以 ScaleType.MATRIX + Matrix 實作縮放（ZoomImageView.kt）。                                   補充      - 目前方案對於瀏覽器情境正確、效能輕量；若之後要做亮度/對比等 per-video 調整，才需要接 Media3 Effects。             
user TextureView.setTransform 與 Media3 Effects 哪一種耗電量高？                                                              codex結論      - 只做縮放時：TextureView.setTransform(Matrix)耗電更低。
- 套用 Media3 Effects（亮度/對比/幾何變換）時：通常更耗電。                                                              為什麼    - TextureView.setTransform：解碼輸出到 SurfaceTexture 後，系統在合成時對單一紋理做矩陣取樣，基本只多一個合成取樣，成
在你這個專案的情境

- 目前已使用 TextureView + ZoomablePlayerView 的 setTransform 做縮放（item_media_viewer.xml 設
app:surface_type="texture_view"）。這比改用 Media3 Effects 來做縮放更省電。
- 若未來需要 per-video 亮度/對比，只能用 Media3 Effects，預期耗電會上升；可減少開銷的方法是把幾何與色彩調整合併為單 一 GL pass，並僅在用戶調整或播放時啟用。

建議

- 縮放：維持 TextureView.setTransform。
- 亮度（整窗）：用 Window.screenBrightness（不改對比、但最省電）。
- 僅變暗：在影片上加黑色半透明 scrim（極低成本）。
- 精準亮度/對比：採用 Media3 Effects，做成選用開關；避免常駐。
- 進一步省電：非縮放時用 SurfaceView，進入「縮放模式」才切到 TextureView（需要在 UI 上做 PlayerView/Surface 切換    策略）。


