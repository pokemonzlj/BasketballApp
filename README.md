# 🏀 篮球王朝

一款基于 Android Jetpack Compose 开发的篮球赛季模拟与比赛计分 APP。体验从常规赛到夺冠的完整赛季历程，支持本地纯单机游玩，也可一键切换为云端数据库同步模式。

## ✨ 核心功能

- **真实赛制模拟**：严格按照 NBA 规则，单赛季共 82 场比赛。APP 会自动生成包含 29 支球队的赛程表，与部分球队交手 3 场，部分交手 2 场，完美还原真实赛季对阵。
- **精细化比赛计分**：支持逐节（第1节、第2节）记录比分。当常规时间结束时若双方比分相同，会自动触发**加时赛**机制，并支持多加时记录。
- **赛季数据看板**：直观展示当前赛季的胜场、负场、胜率及常规赛进度（如 45 / 82 场）。
- **历史比赛回溯**：无论是在本地还是云端，都可以点击任意赛季卡片，查看该赛季每一场比赛的对阵双方、最终比分、胜负结果及加时数（OT）。
- **双模式运行引擎**：
  - **纯本地模式**：不配置任何服务器信息，所有数据通过 SharedPreferences 保存在手机本地，即开即玩。
  - **云端同步模式**：填入数据库配置后，APP 启动会自动拉取云端历史战绩汇总，比赛结束会自动将明细（包含各节比分 JSON）上传至 MySQL 数据库，实现多设备数据同步。

## 🛠 技术栈

- **UI 框架**：100% Kotlin + Jetpack Compose (Material3)
- **异步处理**：Kotlin Coroutines (Dispatchers.IO)
- **本地存储**：Android SharedPreferences
- **云端同步**：MySQL 8.0 (通过 JDBC 直连)
- **极简后端架构**：单表设计（`games` 表），服务器仅作为流水账云盘，所有赛季汇总逻辑由客户端计算分组。

## 🏗 架构与赛程算法

本项目的数据库设计极其精简，全局仅有一张 `games` 记录表。客户端在启动时通过 `GROUP BY` 拉取汇总数据，在内存中动态计算当前赛季进度和历史战绩。

**赛程生成算法**：

1. 取 29 个对手，各安排 2 场比赛（共 58 场）。
2. 从 29 个对手中随机抽取 24 个队伍，各加赛 1 场（共 24 场）。
3. 将这 82 场比赛打散排序，生成一份完整的赛季赛程表并保存在本地，确保每次游玩的赛季对阵都具有随机性但符合总场次规则。

## 🚀 如何配置

### 纯本地模式（默认）

直接编译运行 APK 即可，无需任何额外配置。

### 数据库同步模式

打开 `MainActivity.kt`，找到 `DBConfig` 对象，填入你自己的数据库信息：

```object DBConfig {
    const val HOST = "你的数据库IP"
    const val PORT = 3306
    const val USER = "用户名"
    const val PASS = "密码"
    const val NAME = "数据库名"
    val isEnabled get() = HOST.isNotEmpty() // 只要 HOST 不为空即开启云端同步
}


数据库表结构 SQL 脚本：


```CREATE TABLE games (
    id INT AUTO_INCREMENT PRIMARY KEY,
    season_code VARCHAR(10) NOT NULL COMMENT '赛季编号，如 S1, S2',
    game_num INT NOT NULL COMMENT '该赛季第几场',
    opponent_name VARCHAR(50) NOT NULL COMMENT '对手名称',
    my_total_score INT DEFAULT 0 COMMENT '我方总得分',
    opp_total_score INT DEFAULT 0 COMMENT '对方总得分',
    is_win TINYINT DEFAULT 0 COMMENT '1-胜，0-负',
    quarters_detail JSON COMMENT '各节比分明细JSON',
    played_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '比赛时间',
    UNIQUE KEY uk_season_game (season_code, game_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史比赛总账本';
