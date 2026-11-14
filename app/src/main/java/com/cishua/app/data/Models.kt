package com.cishua.app.data

/** 一个单词条目，索引顺序与 assets 中 JSON 数组一致 */
data class WordEntry(
    val id: Int,
    val word: String,
    val phonetic: String,
    val translation: String,
    val example: String,
    val exampleCn: String,
    val example2: String,
    val example2Cn: String
)

/** 一个词库 */
data class Bank(
    val id: String,
    val name: String,
    val desc: String,
    val words: List<WordEntry>
)

/** 词库元信息（用于首屏快速展示） */
data class BankMeta(
    val id: String,
    val name: String,
    val desc: String,
    val count: Int
)
