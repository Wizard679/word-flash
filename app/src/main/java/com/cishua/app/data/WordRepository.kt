package com.cishua.app.data

import android.content.Context
import org.json.JSONObject

/** 从 assets 读取词库。词库完全离线内置，安装后无需联网。 */
class WordRepository(private val context: Context) {

    fun loadBankMetas(): List<BankMeta> {
        val text = context.assets.open("banks.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr = root.getJSONArray("banks")
        val out = ArrayList<BankMeta>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                BankMeta(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    desc = o.optString("desc"),
                    count = o.optInt("count")
                )
            )
        }
        return out
    }

    fun loadBank(id: String): Bank {
        val text = context.assets.open("words_$id.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr = root.getJSONArray("words")
        val words = ArrayList<WordEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            words.add(
                WordEntry(
                    id = i,
                    word = a.optString(0),
                    phonetic = a.optString(1),
                    translation = a.optString(2),
                    example = a.optString(3),
                    exampleCn = a.optString(4),
                    example2 = a.optString(5),
                    example2Cn = a.optString(6)
                )
            )
        }
        return Bank(
            id = root.optString("id", id),
            name = root.optString("name"),
            desc = root.optString("desc"),
            words = words
        )
    }
}
