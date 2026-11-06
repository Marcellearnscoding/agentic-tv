package com.agentictv.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * People who use the TV. Each has their own app order (the apps they open most
 * come first) and a short list of things the assistant has learned they like.
 *
 * "Guest" is always there and is never saved: it keeps the normal app order
 * and the assistant never remembers anything about guests.
 *
 * Everything is saved in one small file on the TV (files/profiles.json).
 * It never goes to GitHub, and nothing is kept on the server.
 */
data class Person(
    val name: String,
    /** What the assistant has learned, like "loves sci-fi films". At most [MaxLikes]. */
    val likes: List<String> = emptyList(),
    /** How often this person opened each app, by package name. */
    val opens: Map<String, Int> = emptyMap(),
)

const val GuestName = "Guest"
private const val MaxLikes = 20
private const val MaxPeople = 6

data class Profiles(val people: List<Person>, val active: String) {
    /** The person using the TV now, or null for Guest. */
    val current: Person? get() = people.firstOrNull { it.name == active }

    fun switchTo(name: String) = copy(active = if (people.any { it.name == name }) name else GuestName)

    fun add(name: String): Profiles {
        val clean = name.trim().take(20)
        if (clean.isEmpty() || clean.equals(GuestName, ignoreCase = true)) return this
        if (people.any { it.name.equals(clean, ignoreCase = true) } || people.size >= MaxPeople) return switchTo(clean)
        return copy(people = people + Person(clean), active = clean)
    }

    fun remove(name: String) = copy(people = people.filter { it.name != name }, active = GuestName)

    private fun updateCurrent(change: (Person) -> Person): Profiles {
        val me = current ?: return this // Guest: nothing is saved
        return copy(people = people.map { if (it.name == me.name) change(it) else it })
    }

    fun remember(note: String) = updateCurrent { p ->
        val clean = note.trim().take(120)
        if (clean.isEmpty() || p.likes.any { it.equals(clean, ignoreCase = true) }) p
        else p.copy(likes = (p.likes + clean).takeLast(MaxLikes))
    }

    fun forgetLikes() = updateCurrent { it.copy(likes = emptyList()) }

    fun countOpen(pkg: String) = updateCurrent { p -> p.copy(opens = p.opens + (pkg to (p.opens[pkg] ?: 0) + 1)) }

    /** Favourite apps in this person's order: most opened first. Guest keeps the normal order. */
    fun <T> sortForMe(items: List<T>, pkg: (T) -> String): List<T> {
        val opens = current?.opens ?: return items
        return items.sortedByDescending { opens[pkg(it)] ?: 0 } // stable, so ties keep the normal order
    }
}

private fun file(context: Context) = File(context.filesDir, "profiles.json")

fun loadProfiles(context: Context): Profiles {
    val json = runCatching { JSONObject(file(context).readText()) }.getOrNull()
        ?: return Profiles(emptyList(), GuestName)
    val list = json.optJSONArray("people") ?: JSONArray()
    val people = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.map { p ->
        val likes = p.optJSONArray("likes") ?: JSONArray()
        val opens = p.optJSONObject("opens") ?: JSONObject()
        Person(
            name = p.optString("name"),
            likes = (0 until likes.length()).map { likes.optString(it) },
            opens = opens.keys().asSequence().associateWith { opens.optInt(it) },
        )
    }.filter { it.name.isNotBlank() }
    return Profiles(people, GuestName).switchTo(json.optString("active"))
}

fun saveProfiles(context: Context, profiles: Profiles) {
    val json = JSONObject()
        .put("active", profiles.active)
        .put("people", JSONArray(profiles.people.map { p ->
            JSONObject()
                .put("name", p.name)
                .put("likes", JSONArray(p.likes))
                .put("opens", JSONObject(p.opens as Map<*, *>))
        }))
    runCatching { file(context).writeText(json.toString()) }
}
