package com.openaicodex.app.data

data class LanguageOption(val code: String, val nativeName: String, val englishName: String)

/**
 * A broad set of world languages for the first-run picker. This list covers
 * the ISO 639-1 languages with the largest speaker populations plus regional
 * languages explicitly requested (Turkish first, since that's the primary
 * user), and is searchable by native or English name in the UI.
 */
object Languages {
    val ALL: List<LanguageOption> = listOf(
        LanguageOption("tr", "Türkçe", "Turkish"),
        LanguageOption("en", "English", "English"),
        LanguageOption("es", "Español", "Spanish"),
        LanguageOption("zh", "中文", "Chinese"),
        LanguageOption("hi", "हिन्दी", "Hindi"),
        LanguageOption("ar", "العربية", "Arabic"),
        LanguageOption("pt", "Português", "Portuguese"),
        LanguageOption("bn", "বাংলা", "Bengali"),
        LanguageOption("ru", "Русский", "Russian"),
        LanguageOption("ja", "日本語", "Japanese"),
        LanguageOption("de", "Deutsch", "German"),
        LanguageOption("fr", "Français", "French"),
        LanguageOption("ur", "اردو", "Urdu"),
        LanguageOption("id", "Bahasa Indonesia", "Indonesian"),
        LanguageOption("it", "Italiano", "Italian"),
        LanguageOption("ko", "한국어", "Korean"),
        LanguageOption("vi", "Tiếng Việt", "Vietnamese"),
        LanguageOption("fa", "فارسی", "Persian"),
        LanguageOption("pl", "Polski", "Polish"),
        LanguageOption("uk", "Українська", "Ukrainian"),
        LanguageOption("nl", "Nederlands", "Dutch"),
        LanguageOption("ro", "Română", "Romanian"),
        LanguageOption("el", "Ελληνικά", "Greek"),
        LanguageOption("cs", "Čeština", "Czech"),
        LanguageOption("sv", "Svenska", "Swedish"),
        LanguageOption("hu", "Magyar", "Hungarian"),
        LanguageOption("he", "עברית", "Hebrew"),
        LanguageOption("th", "ไทย", "Thai"),
        LanguageOption("az", "Azərbaycanca", "Azerbaijani"),
        LanguageOption("ms", "Bahasa Melayu", "Malay"),
        LanguageOption("fi", "Suomi", "Finnish"),
        LanguageOption("da", "Dansk", "Danish"),
        LanguageOption("no", "Norsk", "Norwegian"),
        LanguageOption("sk", "Slovenčina", "Slovak"),
        LanguageOption("bg", "Български", "Bulgarian"),
        LanguageOption("hr", "Hrvatski", "Croatian"),
        LanguageOption("sr", "Српски", "Serbian"),
        LanguageOption("lt", "Lietuvių", "Lithuanian"),
        LanguageOption("lv", "Latviešu", "Latvian"),
        LanguageOption("et", "Eesti", "Estonian"),
        LanguageOption("sl", "Slovenščina", "Slovenian"),
        LanguageOption("ka", "ქართული", "Georgian"),
        LanguageOption("hy", "Հայերեն", "Armenian"),
        LanguageOption("sq", "Shqip", "Albanian"),
        LanguageOption("mk", "Македонски", "Macedonian"),
        LanguageOption("bs", "Bosanski", "Bosnian"),
        LanguageOption("kk", "Қазақша", "Kazakh"),
        LanguageOption("uz", "O'zbekcha", "Uzbek"),
        LanguageOption("mn", "Монгол", "Mongolian"),
        LanguageOption("km", "ខ្មែរ", "Khmer"),
        LanguageOption("lo", "ລາວ", "Lao"),
        LanguageOption("my", "မြန်မာ", "Burmese"),
        LanguageOption("ne", "नेपाली", "Nepali"),
        LanguageOption("si", "සිංහල", "Sinhala"),
        LanguageOption("ta", "தமிழ்", "Tamil"),
        LanguageOption("te", "తెలుగు", "Telugu"),
        LanguageOption("ml", "മലയാളം", "Malayalam"),
        LanguageOption("kn", "ಕನ್ನಡ", "Kannada"),
        LanguageOption("mr", "मराठी", "Marathi"),
        LanguageOption("gu", "ગુજરાતી", "Gujarati"),
        LanguageOption("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        LanguageOption("am", "አማርኛ", "Amharic"),
        LanguageOption("sw", "Kiswahili", "Swahili"),
        LanguageOption("ha", "Hausa", "Hausa"),
        LanguageOption("yo", "Yorùbá", "Yoruba"),
        LanguageOption("zu", "isiZulu", "Zulu"),
        LanguageOption("af", "Afrikaans", "Afrikaans"),
        LanguageOption("is", "Íslenska", "Icelandic"),
        LanguageOption("ga", "Gaeilge", "Irish"),
        LanguageOption("cy", "Cymraeg", "Welsh"),
        LanguageOption("eu", "Euskara", "Basque"),
        LanguageOption("ca", "Català", "Catalan"),
        LanguageOption("gl", "Galego", "Galician"),
        LanguageOption("mt", "Malti", "Maltese"),
        LanguageOption("tl", "Filipino", "Filipino"),
        LanguageOption("ky", "Кыргызча", "Kyrgyz"),
        LanguageOption("tg", "Тоҷикӣ", "Tajik"),
        LanguageOption("tk", "Türkmençe", "Turkmen"),
        LanguageOption("ps", "پښتو", "Pashto"),
        LanguageOption("ku", "Kurdî", "Kurdish"),
        LanguageOption("so", "Soomaali", "Somali"),
        LanguageOption("xh", "isiXhosa", "Xhosa")
    )

    fun search(query: String): List<LanguageOption> {
        if (query.isBlank()) return ALL
        val q = query.trim().lowercase()
        return ALL.filter {
            it.nativeName.lowercase().contains(q) ||
                it.englishName.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }
}
