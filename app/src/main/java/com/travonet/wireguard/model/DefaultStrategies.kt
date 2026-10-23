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
 * Для Cloudflare WARP проверена следующая последовательность:
 *   junk(64) -> mimic(quic) -> mimic(stun) -> junk(64) ->
 *   fake_udp(148, corrupt=true) -> sleep(15ms) -> handshake
 *
 * Первый junk обязателен: если поток начинается с крупного QUIC-пакета,
 * Cloudflare не отвечает на handshake. Поэтому pre-junk встроен в DSL, а
 * `preJunk` у пресетов выставлен в 0, чтобы ядро не отправляло его дважды.
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
            name = "WARP / Мобильная сеть (Рекомендуемая)",
            description = "Проверенная схема для Cloudflare WARP: pre-junk 64 Б, QUIC + STUN, второй junk, повреждённый fake-handshake и пауза 15 мс. Только userspace, без фрагментов и IP Options.",
            dsl = "junk(size=64) -> mimic(quic) -> mimic(stun) -> junk(size=64) -> fake_udp(size=148, corrupt=true) -> sleep(15ms)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_pure_frag",
            name = "WARP Lite (без fake)",
            description = "Облегчённый вариант: pre-junk, QUIC + STUN и второй junk. Меньше пакетов, но менее стабильно на агрессивном DPI.",
            dsl = "junk(size=64) -> mimic(quic) -> mimic(stun) -> junk(size=64)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_fake_frag",
            name = "WARP без второго junk",
            description = "Минимальная рабочая связка: pre-junk, QUIC + STUN и fake-handshake. Попробуйте, если провайдер режет второй junk-пакет.",
            dsl = "junk(size=64) -> mimic(quic) -> mimic(stun) -> fake_udp(size=148, corrupt=true)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_junk_frag",
            name = "WARP Aggressive (двойной pre-junk)",
            description = "Для сетей, где одиночный pre-junk не проходит: два junk-пакета 40 и 70 Б, затем QUIC + STUN, второй junk, fake-handshake и пауза.",
            dsl = "junk(size=40) -> junk(size=70) -> mimic(quic) -> mimic(stun) -> junk(size=64) -> fake_udp(size=148, corrupt=true) -> sleep(15ms)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_timing_sleep",
            name = "WARP с паузами",
            description = "Для DPI с временным окном: pre-junk, пауза 10 мс, QUIC + STUN, второй junk, пауза 5 мс, fake-handshake и пауза 15 мс.",
            dsl = "junk(size=64) -> sleep(10ms) -> mimic(quic) -> mimic(stun) -> junk(size=64) -> sleep(5ms) -> fake_udp(size=148, corrupt=true) -> sleep(15ms)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_broadband",
            name = "WARP / Проводной интернет (jumbo)",
            description = "Для домашнего Wi-Fi/проводного интернета: pre-junk, QUIC + STUN, крупный jumbo-фейк и fake-handshake с IP Options. В LTE/5G jumbo и IP Options часто отбрасываются.",
            dsl = "junk(size=64) -> mimic(quic) -> mimic(stun) -> jumbo_fake(size=1600) -> fake_udp(size=148, ipopt=ts) -> sleep(2ms)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_fake_between_frags",
            name = "WARP + IP Options (только проводной)",
            description = "Вариант с IP Options и fake-handshake. На LTE/5G IP Options часто отбрасываются; используйте только на проводном интернете.",
            dsl = "junk(size=64) -> mimic(quic) -> mimic(stun) -> fake_udp(size=148, ipopt=ts, corrupt=true) -> sleep(15ms)",
            fakeTtl = "auto",
            preJunk = 0,
            normalTtl = 64,
            isCustom = false
        ),
        EvasionStrategy(
            id = "strat_userspace_socks5",
            name = "WARP для SOCKS5 (Zero-Root)",
            description = "Та же проверенная WARP-схема для режима SOCKS5 без root: pre-junk, QUIC + STUN, второй junk, fake-handshake и пауза.",
            dsl = "junk(size=64) -> mimic(quic) -> mimic(stun) -> junk(size=64) -> fake_udp(size=148, corrupt=true) -> sleep(15ms)",
            fakeTtl = "auto",
            preJunk = 0,
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
