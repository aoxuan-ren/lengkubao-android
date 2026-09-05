# 冷库宝 · 手持端（Android）

冷库仓储现场作业 App：入库、报账出库、包装、预售、预支扣款、收支流水。数据存在本机 Room 库，通过局域网与 **冷库宝电脑端** 双向同步。

面向冷库一线操作员（扫码、开单、查询）。老板看账、切财年、配对设备请用电脑端。

## 功能

- **入库开单**：客户 / 品种 / 库位 / 经办人，扫码录入
- **客户报账**：按库存出库结算
- **包装记账**：按包装类型计费
- **预售出库**：预售单与收款
- **预支扣款**：预支、扣款记录
- **收支流水**：现金流水分类记账
- **查询与统计**：入库单 / 包装单 / 报账单 / 预售单 / 预支扣款 / 入库统计
- **基础配置**：客户、品种、库位、经办人、包装类型
- **同步**：TCP 双向增量同步、全量从电脑拉取、二维码/配对码配对、mDNS / UDP 发现电脑端
- **会计年度**：按年分库（`lengkubao_{年}.db`），与电脑端对齐
- **打印**：商米内置打印机（单据、客户标签）

## 技术栈

| 项 | 说明 |
|----|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room（当前 schema version 35） |
| 最低系统 | Android 7.0（API 24） |
| 编译 | compileSdk / targetSdk 36，AGP 8.12，Kotlin 2.0.21 |
| 包名 | `com.pingwei.lengkubao` |
| 硬件 | 商米手持机（打印 AIDL、扫码 SDK） |

主要目录：

```
app/src/main/java/com/pingwei/lengkubao/
  ui/          界面（开单、查询、配置、同步）
  data/db/     Room 实体与 DAO
  sync/        TCP / 配对 / 发现
  fiscal/      会计年度与分库
  service/     库存、作废、流水等业务
  sdk/         商米打印等设备封装
```

## 环境要求

- Android Studio Ladybug 或更新（含 JDK 17）
- 真机建议商米设备；普通手机可跑业务逻辑，打印/专用扫码可能不可用
- 与电脑端同一局域网；电脑端需已启动同步服务

依赖仓库已在 `settings.gradle.kts` 中配置（Google、Maven Central、阿里云镜像、商米 Maven、JitPack）。本地 AAR 放在 `app/libs/`。

## 构建

```bash
git clone <本仓库 URL>
cd project_mi
```

用 Android Studio 打开根目录，等待 Gradle 同步后：

- Debug：Run `app`
- Release：`Build > Generate Signed Bundle / APK`，或：

```bash
./gradlew :app:assembleRelease
```

Windows 可用 `gradlew.bat`。

首次运行需授予相机（扫码）等权限。TCP 默认可自动启动前台同步服务。

## 与电脑端配合

1. 电脑端启动并打开同步（顶栏显示在线）
2. 手持端「TCP 配置」：扫电脑端配对二维码，或输入配对码 / IP
3. 日常用「双向增量同步」；重装或数据不一致时用「从电脑全量拉取」
4. 两端会计年度必须一致，否则会写到不同年库

默认发现/广播相关端口与电脑端约定（UDP 广播 **8888** 等），防火墙需放行。

## 上传 GitHub 前请排除

不要把客户数据和密钥推上去：

- `local.properties`（本机 SDK 路径）
- `*.db`、备份、导出的 Excel
- `app/release/*.apk`（若含生产数据或签名密钥）
- 签名密钥 `*.jks` / `*.keystore`

## 许可

私有业务软件。未声明开源协议前，禁止未授权复制与商用。
