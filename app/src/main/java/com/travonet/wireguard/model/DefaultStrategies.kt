package com.travonet.wireguard.model

/**
 * Встроенные пресеты стратегий десинхронизации.
 *
 * Все DSL-выражения обязаны укладываться в возможности нерутованного Android:
 * ядро принимает только userspace-действия (`junk`, `fake_udp`, `jumbo_fake`,
 * `mimic`, `ipopt_shift`, `sleep`). Сырые L3-действия (`frag`, `fake_frag`,
 * `badsum=true`) требуют CAP_NET_RAW и отклоняются парсером ядра — тогда
 * молча включается стратегия по умолчанию. Поэтому здесь их быть не должно.
 *
 * Идентификаторы пресетов сохранены ради совместимости с уже сохранённым
 * выбором пользователя (`selected_strategy_id`).
 */
object DefaultStrategies {

    /** Значения DSL, означающие «обход выключен»: ядро получит `NoEvasion = true`. */
    val NO_EVASION_ALIASES = setOf("", "none", "off", "clean", "no", "no-evasion", "no_evasion")

    fun isNoEvasion(dsl: String): Boolean = dsl.trim().lowercase() in NO_EVASION_ALIASES

    /** Действия, которым нужен raw-сокет (CAP_NET_RAW) — на нерутованном Android недоступны. */
    private val RAW_ONLY_ACTION =
        Regex("""\b(frag|fragment|fake_frag|fakefrag|fake_fragment)\s*\(""", RegexOption.IGNORE_CASE)

    fun hasRawOnlyActions(dsl: String): Boolean =
        RAW_ONLY_ACTION.containsMatchIn(dsl) || dsl.contains("badsum", ignoreCase = true)

    val ALL: List<EvasionStrategy> = listOf(
        EvasionStrategy(
            id = "strat_tspu_default",
            name = "Мобильная сеть / Wi-Fi (Рекомендуемая)",
            description = "Безопасный для LTE/5G конвейер: pre-junk 64 байта, затем QUIC + STUN, junk 64 байта, повреждённый fake-handshake и пауза 15 мс. Pre-junk перед QUIC обязателен для WARP: без него Cloudflare не отвечает на handshake. Без фрагментов и IP Options, поэтому пакеты не теряются в сотовой сети.",
            dsl = "mimic(quic) -> mimic(stun) -> junk(size=64) -> fake_udp(size=148, corrupt=true) -> sleep(15ms)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_broadband",
            name = "Проводной интернет / Wi-Fi (jumbo)",
            description = "Старый конвейер с jumbo_fake 1600 байт и IP Options. Подходит для домашнего проводного интернета, но в LTE/5G такие пакеты часто отбрасываются.",
            dsl = "mimic(quic) -> mimic(stun) -> jumbo_fake(size=1600) -> fake_udp(size=148, ipopt=ts) -> sleep(2ms)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_pure_frag",
            name = "Junk + фейковый handshake",
            description = "Мусорный UDP-пакет сбивает сопоставление протокола, следом уходит фейковый handshake с коротким TTL и повреждённым заголовком.",
            dsl = "junk(size=64) -> fake_udp(size=148, ttl=5, corrupt=true)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_junk_frag",
            name = "Pre-Junk + jumbo-фейк",
            description = "Мусорный пакет 64 байта и крупный jumbo-фейк (не меньше 1500 байт) перед легитимным рукопожатием — DPI видит «тяжёлый» поток.",
            dsl = "junk(size=64) -> jumbo_fake(size=1600)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_fake_between_frags",
            name = "Двойной фейк + сдвиг IP-опций",
            description = "Два фейковых пакета, между ними — сдвиг IPv4-опций (ipopt_shift). Сбивает stateful DPI, который запоминает смещения заголовков.",
            dsl = "fake_udp(size=148) -> ipopt_shift(8, mode=ts) -> fake_udp(size=148, ttl=4, corrupt=true)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_timing_sleep",
            name = "Фейки с паузами (Sleep)",
            description = "Микрозадержки 10 мс и 5 мс между junk, fake и jumbo-пакетами для обхода DPI с буферизацией временного окна.",
            dsl = "junk(size=64) -> sleep(10ms) -> fake_udp(size=148) -> sleep(5ms) -> jumbo_fake(size=1600)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_fake_frag",
            name = "Мимикрия QUIC/STUN + фейк",
            description = "Пакеты-мимикрии под QUIC и STUN, затем фейковый handshake с коротким TTL. Без сырых фрагментов — только userspace.",
            dsl = "mimic(quic) -> mimic(stun) -> fake_udp(size=148, ttl=4)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_userspace_socks5",
            name = "Для Userspace SOCKS5 (Zero-Root)",
            description = "Для режима SOCKS5 без прав root: junk-пакет и фейковый handshake, TTL берётся из set-сокета (IP_TTL) без сырых фрагментов.",
            dsl = "junk(size=64) -> fake_udp(size=148)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_clean_wg",
            name = "Чистый WireGuard (Без обхода)",
            description = "Десинхронизация полностью отключена: в конфиг добавляется NoEvasion = true, отправляется только штатный WireGuard.",
            dsl = "",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        )
    )
}
