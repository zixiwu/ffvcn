package com.ffvcn.client.vpn

import java.util.Locale

/**
 * Country names as the Chinese UI should read them, plus the order the region
 * picker shows them in.
 *
 * The exit list comes from Mozilla's own API and carries the country name in
 * English ("Germany") next to a two-letter code. The curated list below is what
 * the picker leads with, in pinyin order; anything the API returns beyond it is
 * appended after. Codes, not names, are the key: "Czechia" and "Czech Republic"
 * are the same exit, and a spelling change on Mozilla's side would otherwise
 * silently drop a region.
 */
object CountryNames {

    /** A curated region: stable code, Chinese name, English name. */
    private data class Preset(val code: String, val zh: String, val en: String)

    /**
     * The regions the picker always lists, in the order it shows them. Kept
     * even when the free tier has no exit there at the moment — the picker
     * greys those out rather than hiding them, so "德国" never just disappears
     * and leave the user wondering what happened to half the list.
     */
    private val preferred: List<Preset> = listOf(
        Preset("AR", "阿根廷", "Argentina"),
        Preset("IE", "爱尔兰", "Ireland"),
        Preset("AT", "奥地利", "Austria"),
        Preset("AU", "澳大利亚", "Australia"),
        Preset("BR", "巴西", "Brazil"),
        Preset("BG", "保加利亚", "Bulgaria"),
        Preset("BE", "比利时", "Belgium"),
        Preset("DK", "丹麦", "Denmark"),
        Preset("DE", "德国", "Germany"),
        Preset("FR", "法国", "France"),
        Preset("PH", "菲律宾", "Philippines"),
        Preset("FI", "芬兰", "Finland"),
        Preset("CO", "哥伦比亚", "Colombia"),
        Preset("KR", "韩国", "South Korea"),
        Preset("NL", "荷兰", "Netherlands"),
        Preset("CA", "加拿大", "Canada"),
        Preset("GH", "加纳", "Ghana"),
        Preset("MY", "马来西亚", "Malaysia"),
        Preset("US", "美国", "United States"),
        Preset("PE", "秘鲁", "Peru"),
        Preset("MX", "墨西哥", "Mexico"),
        Preset("ZA", "南非", "South Africa"),
        Preset("NO", "挪威", "Norway"),
        Preset("PT", "葡萄牙", "Portugal"),
        Preset("JP", "日本", "Japan"),
        Preset("SE", "瑞典", "Sweden"),
        Preset("TH", "泰国", "Thailand"),
        Preset("ES", "西班牙", "Spain"),
        Preset("SG", "新加坡", "Singapore"),
        Preset("NZ", "新西兰", "New Zealand"),
        Preset("IT", "意大利", "Italy"),
        Preset("GB", "英国", "United Kingdom"),
        Preset("CL", "智利", "Chile")
    )

    private val zhByCode: Map<String, String> = preferred.associate { it.code to it.zh }

    private val orderByCode: Map<String, Int> =
        preferred.withIndex().associate { (index, preset) -> preset.code to index }

    /**
     * The curated regions in picker order, named for the current UI language.
     * The picker marks the ones without a live exit; it never drops them.
     */
    fun presets(): List<Pair<String, String>> {
        val zh = Locale.getDefault().language == "zh"
        return preferred.map { it.code to if (zh) it.zh else it.en }
    }

    /** Fallback for exits whose code is not in [preferred]. */
    private val zhByName: Map<String, String> = mapOf(
        "Argentina" to "阿根廷",
        "Australia" to "澳大利亚",
        "Austria" to "奥地利",
        "Belgium" to "比利时",
        "Brazil" to "巴西",
        "Bulgaria" to "保加利亚",
        "Canada" to "加拿大",
        "Chile" to "智利",
        "Colombia" to "哥伦比亚",
        "Croatia" to "克罗地亚",
        "Czechia" to "捷克",
        "Czech Republic" to "捷克",
        "Denmark" to "丹麦",
        "Estonia" to "爱沙尼亚",
        "Finland" to "芬兰",
        "France" to "法国",
        "Germany" to "德国",
        "Ghana" to "加纳",
        "Greece" to "希腊",
        "Hong Kong" to "中国香港",
        "Hungary" to "匈牙利",
        "Iceland" to "冰岛",
        "India" to "印度",
        "Indonesia" to "印度尼西亚",
        "Ireland" to "爱尔兰",
        "Israel" to "以色列",
        "Italy" to "意大利",
        "Japan" to "日本",
        "Korea" to "韩国",
        "Latvia" to "拉脱维亚",
        "Lithuania" to "立陶宛",
        "Luxembourg" to "卢森堡",
        "Malaysia" to "马来西亚",
        "Mexico" to "墨西哥",
        "Netherlands" to "荷兰",
        "New Zealand" to "新西兰",
        "Norway" to "挪威",
        "Peru" to "秘鲁",
        "Philippines" to "菲律宾",
        "Poland" to "波兰",
        "Portugal" to "葡萄牙",
        "Romania" to "罗马尼亚",
        "Serbia" to "塞尔维亚",
        "Singapore" to "新加坡",
        "Slovakia" to "斯洛伐克",
        "Slovenia" to "斯洛文尼亚",
        "South Africa" to "南非",
        "South Korea" to "韩国",
        "Spain" to "西班牙",
        "Sweden" to "瑞典",
        "Switzerland" to "瑞士",
        "Taiwan" to "中国台湾",
        "Thailand" to "泰国",
        "Turkey" to "土耳其",
        "Türkiye" to "土耳其",
        "Ukraine" to "乌克兰",
        "United Arab Emirates" to "阿联酋",
        "United Kingdom" to "英国",
        "United States" to "美国",
        "Vietnam" to "越南"
    )

    /** The name to show for [code]/[countryName], localised when the UI is Chinese. */
    fun display(code: String, countryName: String): String {
        val name = countryName.trim()
        if (Locale.getDefault().language != "zh") {
            return name.ifEmpty { code.trim().uppercase(Locale.US) }
        }
        zhByCode[code.trim().uppercase(Locale.US)]?.let { return it }
        if (name.isEmpty()) return code.trim().uppercase(Locale.US)
        return zhByName[name] ?: name
    }

    /** Sort key: the curated regions first in their fixed order, the rest last. */
    fun rank(code: String): Int =
        orderByCode[code.trim().uppercase(Locale.US)] ?: Int.MAX_VALUE
}
