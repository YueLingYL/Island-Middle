# Island Middle

LSPosed 模块：让平板（HyperOS）的灵动岛缩小态 / 展开态走手机、折叠屏路径。

## 功能

- 拦截 `ro.build.characteristics`（单参 / 两参 `SystemProperties.get`），使插件侧 `IS_PAD = false`
- 兜底写入 `MiuiConfigs.IS_PAD = false`
- Hook 插件 `CommonUtils.getIS_TABLET` 与 `isDynamicIslandPadNoPropOnce`，强制返回 `false`
- Hook `ClassLoader.loadClass` 与 `Application.attach`，在类加载后强制刷新，覆盖 `static final` 缓存
- 模块 App 提供开关、状态查看与 root 强停 SystemUI（使 Hook 立即重载）

## 要求

- LSPosed（Xposed API 82+，`xposedminversion` 93）
- HyperOS 3 及以上
- 作用域：`com.android.systemui`
- `settings get global support_dynamic_island` 返回 `1`

## 安装

1. 安装 Release 中的签名 APK
2. 在 LSPosed 中启用模块并勾选作用域 `com.android.systemui`
3. 强停 SystemUI（模块 App 内置按钮，或自行 `killall com.android.systemui`）

## 构建

```bash
./gradlew assembleDebug     # 调试
./gradlew assembleRelease   # 签名 Release（需 keystore.properties）
```

签名配置见 `keystore.properties`（不入库），字段：`storeFile` / `storePassword` / `keyAlias` / `keyPassword`。

## 说明

- 仅处理给定 `MiuiSystemUI.apk` / 插件逻辑，不改包签名
- 岛窗口由 `miui.systemui.plugin` 绘制，本模块在 SystemUI 进程内影响插件分支
