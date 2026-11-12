package com.ffvcn.client.vpn

import java.util.Locale

/**
 * Country names as the Chinese UI should read them, plus the order the region
 * picker shows them in.
 *
 * The exit list comes from Mozilla's own API and carries the country name in
 * English ("Germany") next to a two-letter code. The names below are the ones
 * people actually look for, so they lead the picker in pinyin order; anything
 * the API returns beyond that list is still selectable and simply follows
 * alphabetically, so a newly opened exit never becomes unreachable.
 */
object CountryNames {
    /**
     * The regions worth putting at the top, in the order the picker shows
     * them: code to Chinese name. Codes, not names, so a spelling change on
     * Mozilla's side cannot silently drop an entry.
     */
    private val preferred: List<Pair<String, String>> = listOf(
        "AR" to "阿根廷",
        "IE" to "爱尔兰",
        "AT" to "奥地利",
        "AU" to "澳大利亚",
        "BR" to "巴西",
        "BG" to "保加利亚",
        "BE" to "比利时",
        "DK" to "丹麦",
        "DE" to "德国",
        "FR" to "法国",
        "PH" to "菲律宾",
        "FI" to "芬兰",
        "CO" to "哥伦比亚",
        "KR" to "韩国",
        "NL" to "荷兰",
        "CA" to "加拿大",
        "GH" to "加纳",
        "MY" to "马来西亚",
        "US" to "美国",
        "PE" to "秘鲁",
        "MX" to "墨西哥",
        "ZA" to "南非",
        "NO" to "挪威",
        "PT" to "葡萄牙",
        "JP" to "日本",
        "SE" to "瑞典",
        "TH" to "泰国",
        "ES" to "西班牙",
        "SG" to "新加坡",
        "NZ" to "新西兰",
        "IT" to "意大利",
        "GB" to "英国",
        "CL" to "智利"
    )

    private val zhByCode: Map<String, String> = preferred.toMap()

    private val orderByCode: Map<String, Int> =
        preferred.withIndex().associate { (index, entry) -> entry.first to index }

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
