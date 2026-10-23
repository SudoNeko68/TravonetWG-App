package com.travonet.wireguard.warp

import android.os.Build
import android.util.Base64
import com.travonet.wireguard.model.WgProfile
import com.travonet.wireguard.parser.WgConfigParser
import org.json.JSONException
import org.json.JSONObject
import org.travonet.backend.GoBackend
import java.io.IOException
import java.net.IDN
import java.net.URL
import java.util.Locale

/**
 * Генерация анонимного профиля Cloudflare WARP.
 *
 * Ключи создаются в Go-ядре на устройстве и никогда не покидают его: в
 * Cloudflare уходит только публичный ключ. Ответ API превращается в обычный
 * WireGuard-конфиг, который дальше живёт как любой другой профиль.
 *
 * Прямая регистрация в некоторых сетях невозможна: провайдер рвёт TLS по имени
 * `api.cloudflareclient.com`. Поэтому дополнительно поддерживается релей —
 * небольшой сервер на незаблокированной площадке, который делает запрос к API
 * за приложение (см. `cmd/warp-relay` в репозитории ядра).
 */
object WarpGenerator {

    internal data class Registration(
        val privateKey: String,
        val publicKey: String,
        val ipv4: String,
        val ipv6: String,
        val peerPublicKey: String,
        val endpoint: String,
        val reserved: String?
    )

    /**
     * Регистрирует устройство и возвращает готовый профиль.
     *
     * @param relayUrl адрес релея; пустая строка — только прямые попытки.
     * @param relayToken необязательный общий секрет релея.
     */
    fun generate(
        name: String,
        relayUrl: String = "",
        relayToken: String = ""
    ): WgProfile {
        val keypairJson = GoBackend.wgGenerateKeypair()
            ?: throw IOException("Ядро не вернуло ключевую пару")
        if (keypairJson.startsWith("ERROR:")) {
            throw IOException(keypairJson.removePrefix("ERROR:").trim())
        }

        val keypair = JSONObject(keypairJson)
        val privateKey = keypair.optString("private_key").trim()
        val publicKey = keypair.optString("public_key").trim()
        if (privateKey.isEmpty() || publicKey.isEmpty()) {
            throw IOException("Ядро вернуло неполную ключевую пару")
        }

        val locale = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
        val model = Build.MODEL?.trim().takeUnless { it.isNullOrEmpty() } ?: "Android"

        val response = GoBackend.wgRegisterWarp(
            publicKey,
            locale,
            model,
            normalizeRelayUrl(relayUrl),
            relayToken.trim()
        ) ?: throw IOException("Ядро не вернуло ответ регистрации")

        if (response.startsWith("ERROR:")) {
            throw IOException(humanize(response.removePrefix("ERROR:").trim()))
        }

        val registration = try {
            parseRegistrationResponse(response, privateKey, publicKey)
        } catch (e: JSONException) {
            throw IOException(
                "Релей вернул не JSON. Проверьте, что на нём развёрнут warp-relay.js.\n\n" +
                    response.take(160)
            )
        }
        return WgConfigParser.parseProfile(name, buildConfig(registration))
    }

    /**
     * Go-ядро получает уже ASCII-хост: так URL с кириллическим доменом
     * (например, larp.футанари.рф) одинаково работает и в DNS, и в TLS SNI.
     */
    internal fun normalizeRelayUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val url = URL(trimmed)
            val host = url.host ?: return trimmed
            val asciiHost = IDN.toASCII(host)
            if (asciiHost == host) return trimmed

            val userInfo = url.userInfo?.let { "$it@" } ?: ""
            val port = if (url.port >= 0) ":${url.port}" else ""
            val fragment = url.ref?.let { "#$it" } ?: ""
            "${url.protocol}://$userInfo$asciiHost$port${url.file}$fragment"
        } catch (_: Exception) {
            trimmed
        }
    }

    /** Уникальное имя, чтобы несколько генераций не сливались в списке. */
    fun nextName(existing: List<WgProfile>): String {
        val base = "WARP (Cloudflare)"
        if (existing.none { it.name == base }) return base
        var index = 2
        while (existing.any { it.name == "$base $index" }) index++
        return "$base $index"
    }

    internal fun parseRegistrationResponse(
        responseText: String,
        privateKey: String,
        publicKey: String
    ): Registration {
        val root = JSONObject(responseText)
        val result = root.optJSONObject("result") ?: root

        val config = result.optJSONObject("config")
            ?: throw IOException("В ответе Cloudflare нет config")
        val peers = config.optJSONArray("peers")
            ?: throw IOException("В ответе Cloudflare нет списка peers")
        if (peers.length() == 0) {
            throw IOException("В ответе Cloudflare пустой список peers")
        }
        val peer = peers.getJSONObject(0)

        val peerPublicKey = peer.optString("public_key").trim()
        val endpoint = endpointOf(peer)
        val iface = config.optJSONObject("interface")
            ?: throw IOException("В ответе Cloudflare нет interface")
        val addresses = iface.optJSONObject("addresses") ?: JSONObject()
        val ipv4 = addresses.optString("v4").trim()
        val ipv6 = addresses.optString("v6").trim()

        if (peerPublicKey.isEmpty()) {
            throw IOException("Cloudflare не вернул публичный ключ узла")
        }
        if (endpoint.isEmpty()) {
            throw IOException("Cloudflare не вернул endpoint узла")
        }
        if (ipv4.isEmpty() && ipv6.isEmpty()) {
            throw IOException("Cloudflare не вернул адреса интерфейса")
        }

        return Registration(
            privateKey = privateKey,
            publicKey = publicKey,
            ipv4 = ipv4,
            ipv6 = ipv6,
            peerPublicKey = peerPublicKey,
            endpoint = endpoint,
            reserved = decodeReserved(config.optString("client_id"))
        )
    }

    internal fun buildConfig(registration: Registration): String {
        val addresses = listOf(registration.ipv4, registration.ipv6)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${registration.privateKey}")
            appendLine("Address = $addresses")
            appendLine("DNS = 1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001")
            appendLine("MTU = 1280")
            registration.reserved?.takeIf { it.isNotBlank() }?.let {
                appendLine("Reserved = $it")
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${registration.peerPublicKey}")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = ${registration.endpoint}")
            appendLine("PersistentKeepalive = 25")
        }.trimEnd() + "\n"
    }

    /**
     * Cloudflare отдаёт endpoint объектом (`v4`, `host`, `v6`). Порядок тот же,
     * что у клиента WARP: сначала IPv4, затем имя, затем IPv6.
     */
    private fun endpointOf(peer: JSONObject): String {
        val endpoint = peer.optJSONObject("endpoint")
        val raw = when {
            endpoint == null -> peer.optString("endpoint")
            endpoint.optString("v4").isNotBlank() -> endpoint.getString("v4")
            endpoint.optString("host").isNotBlank() -> endpoint.getString("host")
            else -> endpoint.optString("v6")
        }
        return withPort(raw.trim())
    }

    private fun withPort(value: String): String {
        if (value.isEmpty()) return ""
        // IPv6: [addr]:port.
        if (value.startsWith("[")) {
            val close = value.lastIndexOf("]:")
            if (close < 0) return "$value:2408"
            val port = value.substring(close + 2).toIntOrNull()
            return if (port == null || port <= 0) value.substring(0, close + 2) + "2408" else value
        }
        val colon = value.lastIndexOf(':')
        if (colon > 0) {
            val port = value.substring(colon + 1).toIntOrNull()
            if (port != null) {
                // Cloudflare returns :0 when the endpoint accepts several
                // ports (see the `ports` field); 2408 is the default one.
                return if (port > 0) value else value.substring(0, colon + 1) + "2408"
            }
        }
        return "$value:2408"
    }

    /** Первые три байта client_id — reserved для WARP-профилей. */
    private fun decodeReserved(clientId: String): String? {
        if (clientId.isBlank()) return null
        return try {
            val bytes = Base64.decode(clientId, Base64.DEFAULT)
            if (bytes.size >= 3) {
                "${bytes[0].toInt() and 0xFF},${bytes[1].toInt() and 0xFF},${bytes[2].toInt() and 0xFF}"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun humanize(message: String): String = when {
        message.contains("relay:") && message.contains("direct:") ->
            "Не удалось зарегистрироваться ни через релей, ни напрямую.\n\n$message"
        else ->
            "Не удалось зарегистрироваться в Cloudflare.\n\n$message"
    }
}
