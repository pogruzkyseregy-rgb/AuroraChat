package ru.avrora.chat

/** Мои вложения для модели превращаются в понятный текст. Собственные теги Авроры остаются как есть. */
fun describeForModel(text: String, stickers: List<CatalogItem>, photos: List<CatalogItem>): String =
    TAG_RE.replace(text) { mr ->
        val id = mr.groupValues[2]
        when (mr.groupValues[1]) {
            "стикер", "sticker" -> {
                val d = stickers.firstOrNull { it.id == id }?.desc
                if (d != null) "[Адонис отправил стикер: $d]" else "[Адонис отправил стикер]"
            }
            "фото", "photo" -> {
                val d = photos.firstOrNull { it.id == id }?.desc
                if (d != null) "[Адонис отправил фото из альбома: $d]" else "[Адонис отправил фото из альбома]"
            }
            else -> "[Адонис прислал фото. Что на нём, ты не видишь: ориентируйся на подпись и контекст.]"
        }
    }.trim()

/** Для сжатия памяти: вложения любой стороны как нейтральные пометки. */
fun toPlainForMemory(text: String, stickers: List<CatalogItem>, photos: List<CatalogItem>): String =
    TAG_RE.replace(text) { mr ->
        val id = mr.groupValues[2]
        when (mr.groupValues[1]) {
            "стикер", "sticker" -> "[стикер: ${stickers.firstOrNull { it.id == id }?.desc ?: id}]"
            "фото", "photo" -> "[фото из альбома: ${photos.firstOrNull { it.id == id }?.desc ?: id}]"
            else -> "[фото от Адониса]"
        }
    }.trim()
