package com.travonet.wireguard.repository

import android.content.Context
import android.content.SharedPreferences
import com.travonet.wireguard.model.DefaultStrategies
import com.travonet.wireguard.model.EvasionStrategy
import com.travonet.wireguard.model.TunnelMode
import com.travonet.wireguard.model.WgProfile
import com.travonet.wireguard.parser.WgConfigParser
import org.json.JSONArray
import org.json.JSONObject

class AppRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("travonet_wg_prefs", Context.MODE_PRIVATE)

    companion object {
        // Пресеты живут в отдельном объекте, чтобы их можно было проверить unit-тестами.
        val DEFAULT_STRATEGIES = DefaultStrategies.ALL

        private const val SAMPLE_CONFIG = """[Interface]
PrivateKey = OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=
Address = 172.16.0.2/32
DNS = 1.1.1.1, 8.8.8.8
MTU = 1280

[Peer]
PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
Endpoint = 162.159.192.1:2408
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25"""
    }

    // Profiles
    fun getProfiles(): List<WgProfile> {
        val raw = prefs.getString("wg_profiles", null)
        if (raw.isNullOrBlank()) {
            val sample = WgConfigParser.parseProfile("Демо-сервер WireGuard (Пример)", SAMPLE_CONFIG)
            saveProfiles(listOf(sample))
            return listOf(sample)
        }
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<WgProfile>()
            var needsResave = false
            for (i in 0 until array.length()) {
                val p = WgProfile.fromJson(array.getJSONObject(i))
                if (p.rawConfig.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")) {
                    val fixed = p.copy(rawConfig = p.rawConfig
                        .replace("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "OCugIHnrk/r8Zdtd5AGYIchdMag+dqXF08yn1AZ2aUw=")
                        .replace("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="))
                    list.add(fixed)
                    needsResave = true
                } else {
                    list.add(p)
                }
            }
            if (needsResave) {
                saveProfiles(list)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
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
}
