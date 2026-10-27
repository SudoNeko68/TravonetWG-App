package com.travonet.wireguard

import com.travonet.wireguard.model.DefaultStrategies
import com.travonet.wireguard.model.EvasionStrategy
import com.travonet.wireguard.parser.WgConfigParser
import com.travonet.wireguard.service.TunnelManager
import com.travonet.wireguard.warp.WarpGenerator
import org.junit.Assert.*
import org.junit.Test

class TravonetUnitTest {

    private val sampleConfigWithoutStrategy = """
        [Interface]
        PrivateKey = OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=
        Address = 172.16.0.2/32, fd00::2/64
        DNS = 1.1.1.1, 8.8.8.8
        MTU = 1280

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        Endpoint = 162.159.192.1:2408
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()

    private val sampleConfigWithStrategy = """
        [Interface]
        PrivateKey = OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=
        Address = 172.16.0.2/32
        DNS = 1.1.1.1
        Strategy = junk(size=32) -> fake_udp(size=148)
        FakeTTL = 9
        PreJunk = 32

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        Endpoint = 162.159.192.1:2408
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    @Test
    fun testParseProfile() {
        val profile = WgConfigParser.parseProfile("My Server", sampleConfigWithoutStrategy)
        assertEquals("My Server", profile.name)
        assertEquals("162.159.192.1:2408", profile.endpoint)
        assertEquals("172.16.0.2/32, fd00::2/64", profile.address)
        assertEquals("0.0.0.0/0, ::/0", profile.allowedIps)
        assertEquals("1.1.1.1, 8.8.8.8", profile.dns)
        assertEquals(1280, profile.mtu)
        assertFalse(profile.hasEmbeddedStrategy)
        assertNull(profile.embeddedStrategy)
    }

    @Test
    fun testParseProfileWithStrategy() {
        val profile = WgConfigParser.parseProfile("Evasion Server", sampleConfigWithStrategy)
        assertTrue(profile.hasEmbeddedStrategy)
        assertEquals("junk(size=32) -> fake_udp(size=148)", profile.embeddedStrategy)
    }

    @Test
    fun testFallbackStrategyInjection() {
        val profile = WgConfigParser.parseProfile("Clean Server", sampleConfigWithoutStrategy)
        val fallbackStrategy = EvasionStrategy(
            name = "Стандартная ТСПУ",
            description = "Test description",
            dsl = "mimic(quic) -> jumbo_fake(size=1600) -> fake_udp(size=148, corrupt=true)",
            fakeTtl = "auto",
            preJunk = 64,
            normalTtl = 64
        )

        val prepared = WgConfigParser.prepareConfigForLaunch(profile, fallbackStrategy)
        assertTrue(prepared.contains("Strategy = mimic(quic) -> jumbo_fake(size=1600) -> fake_udp(size=148, corrupt=true)"))
        assertTrue(prepared.contains("FakeTTL = auto"))
        assertTrue(prepared.contains("PreJunk = 64"))
        assertTrue(prepared.contains("NormalTTL = 64"))
        assertTrue(prepared.contains("Address = 172.16.0.2/32, fd00::2/64"))
    }

    @Test
    fun testConfigWithOwnStrategyNotOverridden() {
        val profile = WgConfigParser.parseProfile("Custom Strategy Server", sampleConfigWithStrategy)
        val fallbackStrategy = EvasionStrategy(
            name = "Fallback",
            description = "Fallback",
            dsl = "junk(size=64) -> fake_udp(size=148)",
            fakeTtl = "auto",
            preJunk = 64
        )

        val prepared = WgConfigParser.prepareConfigForLaunch(profile, fallbackStrategy)
        assertTrue(prepared.contains("Strategy = junk(size=32) -> fake_udp(size=148)"))
        assertFalse(prepared.contains("junk(size=64) -> fake_udp(size=148)"))
    }

    @Test
    fun testParseUapiStats() {
        val uapi = """
            public_key=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            endpoint=162.159.192.1:2408
            last_handshake_time_sec=1728120000
            last_handshake_time_nsec=123456
            tx_bytes=1048576
            rx_bytes=5242880
            persistent_keepalive_interval=25
            allowed_ip=0.0.0.0/0
        """.trimIndent()

        val stats = TunnelManager.parseUapiStats(uapi)
        assertEquals(1048576L, stats.txBytes)
        assertEquals(5242880L, stats.rxBytes)
        assertEquals(1728120000L, stats.lastHandshakeSec)
        assertEquals("1.00 MB", stats.formattedTx())
        assertEquals("5.00 MB", stats.formattedRx())
    }

    @Test
    fun testDefaultStrategiesUseUserspaceActionsOnly() {
        DefaultStrategies.ALL.forEach { strategy ->
            assertFalse(
                "Пресет '${strategy.name}' использует действие, требующее raw-сокет: ${strategy.dsl}",
                DefaultStrategies.hasRawOnlyActions(strategy.dsl)
            )
        }
        assertEquals(DefaultStrategies.ALL.size, DefaultStrategies.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun testRecommendedStrategyStartsWithJunk() {
        val strategy = DefaultStrategies.ALL.first { it.id == "strat_tspu_default" }

        assertTrue(
            "Рекомендуемая WARP-стратегия должна начинаться с junk: ${strategy.dsl}",
            strategy.dsl.trimStart().startsWith("junk(")
        )
        assertTrue(strategy.dsl.contains("mimic(quic)"))
        assertTrue(strategy.dsl.contains("mimic(stun)"))
        assertTrue(strategy.dsl.contains("fake_udp"))
        assertEquals(0, strategy.preJunk)
    }

    @Test
    fun testNoEvasionInjectedForCleanStrategy() {
        val profile = WgConfigParser.parseProfile("Clean Server", sampleConfigWithoutStrategy)
        val clean = DefaultStrategies.ALL.first { it.id == "strat_clean_wg" }

        val prepared = WgConfigParser.prepareConfigForLaunch(profile, clean)
        assertTrue(prepared.contains("NoEvasion = true"))
        assertFalse(prepared.contains("Strategy ="))
        // Директивы обязаны попасть в [Interface], до [Peer].
        assertTrue(prepared.indexOf("NoEvasion = true") < prepared.indexOf("[Peer]"))
    }

    @Test
    fun testNoEvasionAliases() {
        val profile = WgConfigParser.parseProfile("Clean Server", sampleConfigWithoutStrategy)
        listOf("none", "off", "clean", " None ").forEach { alias ->
            val strategy = EvasionStrategy(name = "Alias", description = "", dsl = alias)
            val prepared = WgConfigParser.prepareConfigForLaunch(profile, strategy)
            assertTrue("alias '$alias'", prepared.contains("NoEvasion = true"))
            assertFalse("alias '$alias'", prepared.contains("Strategy ="))
        }
    }

    @Test
    fun testConfigWithNoEvasionNotOverridden() {
        val raw = """
            [Interface]
            PrivateKey = OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=
            Address = 172.16.0.2/32
            NoEvasion = true

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 162.159.192.1:2408
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val profile = WgConfigParser.parseProfile("Raw NoEvasion", raw)
        val strategy = DefaultStrategies.ALL.first { it.id == "strat_tspu_default" }

        val prepared = WgConfigParser.prepareConfigForLaunch(profile, strategy)
        assertEquals(raw, prepared)
    }

    @Test
    fun testNoEvasionFalseStillAllowsInjection() {
        val raw = """
            [Interface]
            PrivateKey = OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=
            Address = 172.16.0.2/32
            NoEvasion = false

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 162.159.192.1:2408
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val profile = WgConfigParser.parseProfile("NoEvasion=false", raw)
        val strategy = DefaultStrategies.ALL.first { it.id == "strat_pure_frag" }

        val prepared = WgConfigParser.prepareConfigForLaunch(profile, strategy)
        assertTrue(prepared.contains("Strategy = ${strategy.dsl}"))
    }

    @Test
    fun testWarpProfileDoesNotEmitReserved() {
        val registration = WarpGenerator.Registration(
            privateKey = "private",
            publicKey = "public",
            ipv4 = "172.16.0.2",
            ipv6 = "2606:4700:110::1",
            peerPublicKey = "peer",
            endpoint = "162.159.192.6:2408"
        )

        val config = WarpGenerator.buildConfig(registration)

        assertFalse(
            "Свежий WARP-профиль не должен содержать Reserved: Cloudflare не отвечает на такой handshake",
            config.contains("Reserved", ignoreCase = true)
        )
        assertTrue(config.contains("Endpoint = 162.159.192.6:2408"))
    }

    @Test
    fun testResolveEndpointIpv4Preserved() {
        val endpoint = "162.159.192.1:2408"
        val resolved = WgConfigParser.resolveEndpointToIpv4(endpoint)
        assertEquals("162.159.192.1:2408", resolved)
    }

    @Test
    fun testResolveEndpointDomainLocalhost() {
        val endpoint = "localhost:51820"
        val resolved = WgConfigParser.resolveEndpointToIpv4(endpoint)
        assertEquals("127.0.0.1:51820", resolved)
    }

    @Test
    fun testResolveConfigEndpointsReplacesDomain() {
        val raw = """
            [Interface]
            PrivateKey = OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=
            Address = 172.16.0.2/32
            
            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = localhost:2408
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val resolved = WgConfigParser.resolveConfigEndpoints(raw)
        assertTrue(resolved.contains("Endpoint = 127.0.0.1:2408"))
        assertFalse(resolved.contains("localhost:2408"))
    }

    @Test
    fun testNormalizeRelayUrlConvertsCyrillicDomainToPunycode() {
        val raw = "https://larp.футанари.рф/register"
        val normalized = WarpGenerator.normalizeRelayUrl(raw)
        assertEquals("https://larp.xn--80aaysqigh.xn--p1ai/register", normalized)
    }

    @Test
    fun testDemoProfileIdentified() {
        val sample = WgConfigParser.parseProfile("Демо-сервер WireGuard (Пример)", sampleConfigWithoutStrategy)
        assertTrue(sample.name.lowercase().contains("демо"))
    }

    @Test
    fun testPingHelperExtractHost() {
        assertEquals("162.159.192.1", com.travonet.wireguard.service.PingHelper.extractHost("162.159.192.1:2408"))
        assertEquals("engage.cloudflareclient.com", com.travonet.wireguard.service.PingHelper.extractHost("engage.cloudflareclient.com:2408"))
        assertEquals("2606:4700:d0::a29f:c001", com.travonet.wireguard.service.PingHelper.extractHost("[2606:4700:d0::a29f:c001]:2408"))
        assertEquals("1.1.1.1", com.travonet.wireguard.service.PingHelper.extractHost("1.1.1.1"))
    }
}
