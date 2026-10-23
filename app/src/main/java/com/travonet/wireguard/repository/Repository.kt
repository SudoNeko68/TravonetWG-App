package com.travonet.wireguard.repository

import android.content.Context
import android.content.SharedPreferences
import com.travonet.wireguard.BuildConfig
import com.travonet.wireguard.model.DefaultStrategies
import com.travonet.wireguard.model.EvasionStrategy
import com.travonet.wireguard.model.TunnelMode
import com.travonet.wireguard.model.WarpRelayMode
import com.travonet.wireguard.model.WgProfile
import org.json.JSONArray
import org.json.JSONObject

class AppRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("travonet_wg_prefs", Context.MODE_PRIVATE)

    companion object {
        // Пресеты живут в отдельном объекте, чтобы их можно было проверить unit-тестами.
        val DEFAULT_STRATEGIES = DefaultStrategies.ALL

        // Раньше приложение при первом запуске создавало этот демо-профиль.
        // Оставляем идентификатор, чтобы удалить его у тех, кто уже обновился.
        private const val LEGACY_SAMPLE_NAME = "Демо-сервер WireGuard (Пример)"
        private const val LEGACY_SAMPLE_PRIVATE_KEY =
            "OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw="
    }

    // Profiles
    fun getProfiles(): List<WgProfile> {
        val raw = prefs.getString("wg_profiles", null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<WgProfile>()
            var needsResave = false
            for (i in 0 until array.length()) {
                val p = WgProfile.fromJson(array.getJSONObject(i))
                if (isLegacySample(p) || p.rawConfig.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")) {
                    needsResave = true
                    continue
                }
                list.add(p)
            }
            if (needsResave) {
                saveProfiles(list)
                val selected = prefs.getString("selected_profile_id", null)
                if (selected != null && list.none { it.id == selected }) {
                    prefs.edit().remove("selected_profile_id").apply()
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isLegacySample(profile: WgProfile): Boolean {
        return profile.name == LEGACY_SAMPLE_NAME &&
            profile.rawConfig.contains(LEGACY_SAMPLE_PRIVATE_KEY)
    }

    fun saveProfiles(profiles: List<WgProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString("wg_profiles", array.toString()).apply()
    }

    fun addOrUpdateProfile(profile: WgProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveProfiles(current)
    }

    fun deleteProfile(id: String) {
        val current = getProfiles().toMutableList()
        current.removeAll { it.id == id }
        saveProfiles(current)
        if (getSelectedProfileId() == id) {
            setSelectedProfileId(current.firstOrNull()?.id)
        }
    }

    fun getSelectedProfileId(): String? {
        val id = prefs.getString("selected_profile_id", null)
        if (id != null) return id
        return getProfiles().firstOrNull()?.id
    }

    fun setSelectedProfileId(id: String?) {
        prefs.edit().putString("selected_profile_id", id).apply()
    }

    fun getSelectedProfile(): WgProfile? {
        val id = getSelectedProfileId() ?: return null
        return getProfiles().firstOrNull { it.id == id }
    }

    // Strategies
    fun getStrategies(): List<EvasionStrategy> {
        val raw = prefs.getString("custom_strategies", null)
        val customList = mutableListOf<EvasionStrategy>()
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    customList.add(EvasionStrategy.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return DEFAULT_STRATEGIES + customList
    }

    fun addOrUpdateCustomStrategy(strategy: EvasionStrategy) {
        val customOnly = getStrategies().filter { it.isCustom }.toMutableList()
        val index = customOnly.indexOfFirst { it.id == strategy.id }
        if (index >= 0) {
            customOnly[index] = strategy.copy(isCustom = true)
        } else {
            customOnly.add(strategy.copy(isCustom = true))
        }
        val array = JSONArray()
        customOnly.forEach { array.put(it.toJson()) }
        prefs.edit().putString("custom_strategies", array.toString()).apply()
    }

    fun deleteCustomStrategy(id: String) {
        val customOnly = getStrategies().filter { it.isCustom && it.id != id }
        val array = JSONArray()
        customOnly.forEach { array.put(it.toJson()) }
        prefs.edit().putString("custom_strategies", array.toString()).apply()
        if (getSelectedStrategyId() == id) {
            setSelectedStrategyId("strat_tspu_default")
        }
    }

    fun getSelectedStrategyId(): String {
        return prefs.getString("selected_strategy_id", "strat_tspu_default") ?: "strat_tspu_default"
    }

    fun setSelectedStrategyId(id: String) {
        prefs.edit().putString("selected_strategy_id", id).apply()
    }

    fun getSelectedStrategy(): EvasionStrategy {
        val id = getSelectedStrategyId()
        return getStrategies().firstOrNull { it.id == id } ?: DEFAULT_STRATEGIES.first()
    }

    // Tunnel Mode
    fun getTunnelMode(): TunnelMode {
        val modeStr = prefs.getString("tunnel_mode", TunnelMode.VPN.name)
        return try {
            TunnelMode.valueOf(modeStr ?: TunnelMode.VPN.name)
        } catch (e: Exception) {
            TunnelMode.VPN
        }
    }

    fun setTunnelMode(mode: TunnelMode) {
        prefs.edit().putString("tunnel_mode", mode.name).apply()
    }

    // SOCKS5 address
    fun getSocksListenAddr(): String {
        return prefs.getString("socks_listen_addr", "127.0.0.1:1080") ?: "127.0.0.1:1080"
    }

    fun setSocksListenAddr(addr: String) {
        prefs.edit().putString("socks_listen_addr", addr).apply()
    }

    // WARP registration relay. The mode decides whether the build default or
    // the user's custom URL is used, so the user can switch back and forth
    // without losing the custom value.
    fun getWarpRelayMode(): WarpRelayMode {
        val stored = prefs.getString("warp_relay_mode", null)
        if (stored != null) {
            return runCatching { WarpRelayMode.valueOf(stored) }
                .getOrDefault(WarpRelayMode.NONE)
        }

        // Migration from builds that only stored a URL. An explicitly empty
        // URL meant "direct", a non-empty one meant "custom relay".
        if (prefs.contains("warp_relay_url")) {
            return if (prefs.getString("warp_relay_url", "").isNullOrBlank()) {
                WarpRelayMode.NONE
            } else {
                WarpRelayMode.CUSTOM
            }
        }

        return if (BuildConfig.WARP_RELAY_URL.isNotBlank()) {
            WarpRelayMode.AUTO
        } else {
            WarpRelayMode.NONE
        }
    }

    fun setWarpRelayMode(mode: WarpRelayMode) {
        prefs.edit().putString("warp_relay_mode", mode.name).apply()
    }

    fun getWarpRelayUrl(): String {
        return when (getWarpRelayMode()) {
            WarpRelayMode.AUTO -> BuildConfig.WARP_RELAY_URL
            WarpRelayMode.CUSTOM -> getCustomWarpRelayUrl()
            WarpRelayMode.NONE -> ""
        }
    }

    fun getWarpRelayToken(): String {
        return when (getWarpRelayMode()) {
            WarpRelayMode.AUTO -> BuildConfig.WARP_RELAY_TOKEN
            WarpRelayMode.CUSTOM -> getCustomWarpRelayToken()
            WarpRelayMode.NONE -> ""
        }
    }

    fun getBuiltInWarpRelayUrl(): String = BuildConfig.WARP_RELAY_URL

    fun getCustomWarpRelayUrl(): String {
        return prefs.getString("warp_relay_url", "") ?: ""
    }

    fun getCustomWarpRelayToken(): String {
        return prefs.getString("warp_relay_token", "") ?: ""
    }

    /** Saves the selected mode and, for CUSTOM, the URL/token pair. */
    fun saveWarpRelaySettings(mode: WarpRelayMode, url: String, token: String) {
        val editor = prefs.edit().putString("warp_relay_mode", mode.name)
        if (mode == WarpRelayMode.CUSTOM) {
            editor.putString("warp_relay_url", url.trim())
            editor.putString("warp_relay_token", token.trim())
        }
        editor.apply()
    }

    // Kept for the quick relay dialog opened from a failed WARP registration.
    fun setWarpRelayUrl(url: String) {
        val trimmed = url.trim()
        prefs.edit()
            .putString("warp_relay_url", trimmed)
            .putString(
                "warp_relay_mode",
                if (trimmed.isBlank()) WarpRelayMode.NONE.name else WarpRelayMode.CUSTOM.name
            )
            .apply()
    }

    fun setWarpRelayToken(token: String) {
        prefs.edit().putString("warp_relay_token", token.trim()).apply()
    }
}
