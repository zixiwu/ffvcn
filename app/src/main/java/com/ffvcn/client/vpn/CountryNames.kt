package com.ffvcn.client.vpn

import java.util.Locale

/**
 * Country names as the Chinese UI should read them.
 *
 * The exit list comes from Mozilla's own API and carries the country name in
 * English ("Germany", "United States"). Translating those by hand keeps the
 * server picker readable in Chinese instead of leaving a list of English names
 * under a Chinese title. Anything not in the table falls back to the name the
 * API sent, so a new exit country never shows up blank.
 */
object CountryNames {
    private val zh: Map<String, String> = mapOf(
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
        "Korea" to "韩国",
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

    /** The name to show for [countryName], localised when the UI is Chinese. */
    fun display(countryName: String): String {
        val name = countryName.trim()
        if (name.isEmpty()) return name
        return if (Locale.getDefault().language == "zh") zh[name] ?: name else name
    }
}
