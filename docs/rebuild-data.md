# 从原始语料重建词库

仓库已内置生成好的词库资源（`app/src/main/assets/words_*.json`），通常无需重建。
如需从零构建，按下面步骤操作。脚本为纯 Python，可重复运行。

## 1. 准备原始数据

| 文件 | 下载地址 |
| --- | --- |
| ECDICT 全量 CSV | https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv |
| CMUdict | https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict |
| Tatoeba 英文句 | https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2 |
| Tatoeba 中文句 | https://downloads.tatoeba.org/exports/per_language/cmn/cmn_sentences.tsv.bz2 |
| Tatoeba 英中链接 | https://downloads.tatoeba.org/exports/per_language/eng/eng-cmn_links.tsv.bz2 |

建议放在同一个目录，例如 `/tmp/openwords/`，脚本直接读取 `.bz2`，无需手动解压。

## 2. 安装构建依赖

```bash
pip install opencc-python-reimplemented
```

OpenCC 只用于把 Tatoeba 的繁体中文转成简体，构建期使用，不会打进 APK。

## 3. 运行脚本

```bash
python3 tools/build_open_words.py \
  --ecdict /tmp/openwords/ecdict.csv \
  --cmudict /tmp/openwords/cmudict.dict \
  --tatoeba /tmp/openwords \
  --out app/src/main/assets \
  --sxb-list app/src/main/assets/words_sxb.json
```

脚本会：

1. 按标签并集筛词（四级 = cet4 ∪ gk ∪ zk，六级 = cet6 ∪ cet4 ∪ gk ∪ zk，专升本 = 大纲词表 ∪ gk）；
2. 清洗 ECDICT 释义：去领域标签、统一词性写法、按义项去重、限 2 行；
3. 音标：CMUdict → ARPAbet 按规则转为美式 IPA，缺失时回退清洗后的 ECDICT 音标；
4. 例句：Tatoeba 英中平行句按词索引、质量过滤、按英文去重、不雅词过滤，中文转简体；
5. 输出 `banks.json`、`words_cet4.json`、`words_cet6.json`、`words_sxb.json`。

## 4. 校验

```bash
./gradlew testDebugUnitTest --tests "com.cishua.app.WordAssetsTest"
```

`WordAssetsTest` 会校验词数与元信息一致、释义非空且去重、例句包含目标词、音标仅含合法 IPA 字符。
