package com.durandestebe.jrls

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Une carte = l'association entre l'UID d'un tag NFC et un dossier de musique
 * (URI SAF de l'arborescence choisie par le parent).
 */
data class Card(
    val uid: String,
    val title: String,
    val treeUri: String
)

/**
 * Persistance des cartes et réglages. MVP : SharedPreferences + org.json
 * (aucune dépendance). Migration vers Room prévue à l'étape 2+ du plan.
 */
class CardRegistry(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jrls", Context.MODE_PRIVATE)

    // --- Réglages parents ---

    fun pin(): String = prefs.getString("pin", "") ?: ""
    fun setPin(pin: String) = prefs.edit().putString("pin", pin).apply()

    fun stopUid(): String = prefs.getString("stopUid", "") ?: ""
    fun setStopUid(uid: String) = prefs.edit().putString("stopUid", uid).apply()

    fun volumeMaxPct(): Int = prefs.getInt("volumeMaxPct", 60)

    fun setVolumeMaxPct(pct: Int) =
        prefs.edit().putInt("volumeMaxPct", pct.coerceIn(0, 100)).apply()

    // --- Cartes ---

    fun cards(): List<Card> {
        val raw = prefs.getString("cards", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Card(o.getString("uid"), o.getString("title"), o.getString("treeUri"))
        }
    }

    fun card(uid: String): Card? = cards().firstOrNull { it.uid == uid }

    fun save(card: Card) {
        val list = cards().filter { it.uid != card.uid }.toMutableList()
        list.add(card)
        putCards(list)
    }

    fun delete(uid: String) = putCards(cards().filter { it.uid != uid })

    private fun putCards(list: List<Card>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("uid", it.uid)
                    .put("title", it.title)
                    .put("treeUri", it.treeUri)
            )
        }
        prefs.edit().putString("cards", arr.toString()).apply()
    }

    // --- Reprise de lecture ---

    fun position(uid: String): Long = prefs.getLong("pos_$uid", 0L)
    fun setPosition(uid: String, pos: Long) = prefs.edit().putLong("pos_$uid", pos).apply()
}
