# 架构说明

```
MainActivity
└── FeedScreen (Compose)
    ├── TopBar            词库切换 / 每日进度
    ├── FeedContent       HorizontalPager 竖向信息流
    │   ├── IntroCard     单词 + 音标 + 释义 + 例句
    │   ├── QuizCard      四选一
    │   ├── SpellCard     听音拼写
    │   └── DoneCard      本组刷完
    ├── ActionBar         底部「不熟 / 熟 / 熟练」大按钮
    └── Panels            设置 / 统计抽屉
```

## 数据

- `WordRepository`：启动时把 assets 中的 JSON 解析成内存列表，按词库缓存。
- `ProgressStore`：SQLite 保存每个词的权重、阶段、下次复习时间、四选一连对次数、熟练标记。
- `WordEngine`：纯函数式的状态流转与权重计算，便于单元测试。

## 卡片流转

| 当前状态 | 触发 | 下一状态 |
| --- | --- | --- |
| 新词（stage 0） | 点「熟」 | 四选一 |
| 四选一 | 答错 | 四选一（权重 +22，重置连对） |
| 四选一 | 连对 3 次 | 拼写 |
| 拼写 | 拼对 | 熟（权重下调，进入长间隔复习队列） |
| 拼写 | 拼错 | 回退四选一（权重 +25） |

## 调度

信息流按 `权重 × 逾期程度` 加权抽取：熟练词权重压到最低且 30 天内不进入候选；
新词受每日上限约束；权重高的词会被提前插入到信息流靠前位置。

## 自动翻页

`advanceTo(from)` 在按钮点击或答题判定后调用：
先 `ensureCards()` 补齐候选，再 `pagerState.animateScrollToPage(target)`，
并校验 `pagerState.currentPage == from`，避免用户已手动滑走时错位。
