package com.radaroccaz.app.sources

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Nuxt 3 pages embed their state as a "devalue" array: objects and arrays hold indexes
 * into that array instead of values, and wrappers like ["ShallowReactive", 3] mark
 * reactive data. This rebuilds plain JSON; cycles and very deep trees are cut.
 */
class NuxtData private constructor(private val flat: JsonArray, private val maxDepth: Int) {

    private fun at(index: Int, depth: Int, path: Set<Int>): JsonElement {
        if (index < 0 || index >= flat.size || depth > maxDepth || index in path) return JsonNull
        val next = path + index
        return when (val v = flat[index]) {
            is JsonObject -> JsonObject(v.mapValues { (_, ref) -> follow(ref, depth, next) })
            is JsonArray -> {
                val head = (v.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
                when {
                    head in WRAPPERS && v.size == 2 -> follow(v[1], depth, next)
                    head in SPECIAL -> JsonNull
                    else -> JsonArray(v.map { follow(it, depth, next) })
                }
            }
            else -> v
        }
    }

    /** Values inside objects and arrays are indexes into the flat array. */
    private fun follow(ref: JsonElement, depth: Int, path: Set<Int>): JsonElement {
        val i = (ref as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return JsonNull
        return at(i, depth + 1, path)
    }

    companion object {
        private val WRAPPERS = setOf("ShallowReactive", "Reactive", "Ref", "ShallowRef", "EmptyRef", "EmptyShallowRef")
        private val SPECIAL = setOf("Date", "Set", "Map", "null", "undefined", "NuxtError", "BigInt", "RegExp")

        fun decode(flat: JsonArray, maxDepth: Int = 40): JsonElement = NuxtData(flat, maxDepth).at(0, 0, emptySet())

        /** Every object of [flat] that satisfies [accept], each decoded on its own (cheaper than decoding the whole page). */
        fun objects(flat: JsonArray, maxDepth: Int = 12, accept: (JsonObject) -> Boolean): List<JsonObject> {
            val d = NuxtData(flat, maxDepth)
            return flat.indices.filter { (flat[it] as? JsonObject)?.let(accept) == true }
                .mapNotNull { d.at(it, 0, emptySet()) as? JsonObject }
        }
    }
}
