# FloatingMemo · 悬浮待办便签

An Android to-do note that floats on top of every app. Collapsed, it is a slim semi-transparent blue line at the edge of the screen; tap it to open the note, tap **–** to fold it away.

一个浮在所有 app 之上的安卓待办便签。收起时是屏幕边缘一条半透明的蓝色细线，点一下展开成便签，点 **–** 收回。

> **Status / 状态**: the code has not been built or run on a device yet. If the first build shows errors, fix them from the red messages in Android Studio.
> 代码尚未在真机或模拟器上编译运行过。第一次构建如果报错，按 Android Studio 里的红字提示修改。

---

## 中文说明

### 功能

- 一条半透明细线浮在所有 app 之上，按住可以拖到任意位置。
- 点细线展开便签，点右上角 **–** 收回；按住标题 **To-do** 可以拖动便签。
- 展开后可以直接改文字、打勾、删除（**✕**）、在底部添加新待办。
- 打勾的条目只是划掉，不会消失，误点了再点一下就恢复；点 **Clear done** 才真正清除。
- 内容保存在手机本地，重启后还在。

### 仓库里的文件

| 文件 | 作用 |
|---|---|
| `AndroidManifest.xml` | 声明权限和组件 |
| `MainActivity.kt` | 设置页面：授权、开启和关闭悬浮便签 |
| `FloatingMemoService.kt` | 悬浮便签本体 |

这里只有三个源文件，不是完整的 Android Studio 项目。按下面的步骤新建项目后把它们放进去。

### 怎么做

1. **装 Android Studio**：在 developer.android.com/studio 下载，Windows 或 Mac 都行，一路默认安装。
2. **新建项目**：**New Project → Empty Views Activity → Next**，然后按下面填：
   - **Name**：`FloatingMemo`
   - **Package name**：`com.example.floatingmemo`（必须一字不差，代码里用的是这个）
   - **Language**：Kotlin
   - **Minimum SDK**：API 26
   - 点 **Finish**，等底部进度条跑完（第一次要下载几分钟）。
3. **替换文件**：左侧文件树顶部的下拉菜单从 **Android** 切到 **Project**，然后：
   - 打开 `app/src/main/AndroidManifest.xml`，全选删除，粘贴本仓库 `AndroidManifest.xml` 的内容。
   - 打开 `app/src/main/java/com/example/floatingmemo/MainActivity.kt`，全选删除，粘贴本仓库的 `MainActivity.kt`。
   - 右键 `floatingmemo` 这个文件夹 → **New → Kotlin Class/File**，命名 `FloatingMemoService`，粘贴本仓库的 `FloatingMemoService.kt`。
4. **运行**：点顶部绿色的 **▶**。
   - 没有安卓手机：用自带的模拟器。**Device Manager → Create Virtual Device**，选一个 Pixel 机型，一路 Next，电脑上就会出现一台虚拟手机，免费。
   - 有安卓手机：手机上 **Settings → About phone**，连点 **Build number** 七次打开开发者模式，再到 **Developer options** 打开 **USB debugging**，用数据线连电脑，在顶部设备列表里选它。
5. **第一次打开 app**：允许通知，点 **1. Allow display over other apps** 并把开关打开，返回后点 **2. Start floating memo**，按 Home 键就能在屏幕左边缘看到蓝色细线。

### 需要知道的两点

- 通知栏会一直有一条 “FloatingMemo is running”。这是安卓的要求，后台常驻的悬浮窗必须有它，不能去掉。
- 有些品牌（小米、华为、OPPO 等）会主动杀后台。细线偶尔消失的话，要在系统设置里把这个 app 的电池优化关掉，并允许自启动。

### 改细线的颜色和粗细

打开 `FloatingMemoService.kt`，改顶部这三行：

```kotlin
private const val HANDLE_COLOR = 0x991E88E5.toInt()   // 蓝色，60% 不透明
private const val HANDLE_WIDTH_DP = 5                  // 粗细
private const val HANDLE_HEIGHT_DP = 56                // 长度
```

颜色格式是 `0xAARRGGBB`，前两位是不透明度（`99` 约 60%，`66` 约 40%，`FF` 完全不透明）。想换成绿色，用 `0x9943A047`。

---

## English

### Features

- A slim semi-transparent line floats above every app. Press and drag to move it anywhere.
- Tap the line to open the note; tap **–** in the top-right corner to fold it. Drag the note by its **To-do** title.
- When open, you can edit text in place, tick tasks off, delete them (**✕**), and add new ones at the bottom.
- A ticked task is only struck through, not removed, so an accidental tap is undone by tapping again. **Clear done** removes ticked tasks for good.
- Tasks are stored on the phone and survive a restart.

### Files in this repository

| File | Purpose |
|---|---|
| `AndroidManifest.xml` | Declares permissions and components |
| `MainActivity.kt` | Settings screen: grant permission, start and stop the note |
| `FloatingMemoService.kt` | The floating memo itself |

These are three source files, not a full Android Studio project. Create a project with the steps below and drop them in.

### How to build it

1. **Install Android Studio**: download it from developer.android.com/studio (Windows or Mac) and accept the default options.
2. **Create a project**: **New Project → Empty Views Activity → Next**, then fill in:
   - **Name**: `FloatingMemo`
   - **Package name**: `com.example.floatingmemo` (must match exactly; the code uses it)
   - **Language**: Kotlin
   - **Minimum SDK**: API 26
   - Click **Finish** and wait for the progress bar at the bottom to finish (the first run downloads for a few minutes).
3. **Replace the files**: switch the drop-down above the file tree from **Android** to **Project**, then:
   - Open `app/src/main/AndroidManifest.xml`, delete everything, and paste the contents of this repository's `AndroidManifest.xml`.
   - Open `app/src/main/java/com/example/floatingmemo/MainActivity.kt`, delete everything, and paste this repository's `MainActivity.kt`.
   - Right-click the `floatingmemo` folder → **New → Kotlin Class/File**, name it `FloatingMemoService`, and paste this repository's `FloatingMemoService.kt`.
4. **Run**: click the green **▶** at the top.
   - No Android phone: use the built-in emulator. **Device Manager → Create Virtual Device**, pick a Pixel model, click Next through the dialogs, and a free virtual phone appears on your computer.
   - With an Android phone: on the phone go to **Settings → About phone** and tap **Build number** seven times to enable developer mode, then turn on **USB debugging** under **Developer options**, connect the phone by cable, and pick it in the device list at the top.
5. **First launch**: allow notifications, tap **1. Allow display over other apps** and turn the switch on, go back, tap **2. Start floating memo**, then press Home. The blue line is on the left edge of the screen.

### Two things to know

- The notification "FloatingMemo is running" stays in the status bar. Android requires it for an overlay that keeps running in the background, so it cannot be removed.
- Some brands (Xiaomi, Huawei, OPPO and others) kill background apps aggressively. If the line disappears now and then, turn off battery optimization for this app in system settings and allow it to auto-start.

### Changing the line's colour and size

Open `FloatingMemoService.kt` and edit these three lines near the top:

```kotlin
private const val HANDLE_COLOR = 0x991E88E5.toInt()   // blue, 60% opaque
private const val HANDLE_WIDTH_DP = 5                  // thickness
private const val HANDLE_HEIGHT_DP = 56                // length
```

The colour format is `0xAARRGGBB`; the first two digits are opacity (`99` is about 60%, `66` about 40%, `FF` fully opaque). For green, use `0x9943A047`.
