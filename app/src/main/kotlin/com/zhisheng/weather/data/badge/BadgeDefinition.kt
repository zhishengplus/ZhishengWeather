package com.zhisheng.weather.data.badge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable internal data class BadgeAnchorSpec(val position: List<Double>, val normal: List<Double>,
    val up: List<Double>, val width: Double, val height: Double)
@Serializable internal data class BadgePresentationSpec(val version: Int, val orientation: List<Double>,
    val occupancy: Double, val maxZoom: Double, val anchors: Map<String, BadgeAnchorSpec> = emptyMap(),
    val materialPolicy: String, val labelStyle: String = "serial")
@Serializable internal data class BadgeDefinitionAsset(val assetId: String, val version: String,
    val sha256: String, val bytes: Long, val mime: String)
@Serializable internal data class BadgeDefinition(val schemaVersion: Int, val badgeId: String,
    val revision: Int, val name: String, val state: String, val renderer: String,
    val asset: BadgeDefinitionAsset, val presentation: BadgePresentationSpec)
@Serializable internal data class BadgeDefinitionEnvelope(val protocol: Int, val purpose: String,
    val definition: BadgeDefinition)

internal fun validateBadgeDefinition(value: BadgeDefinition, grant: BadgeGrant) {
    require(value.schemaVersion == 1 && value.badgeId == grant.badgeId && value.revision == grant.definitionRevision)
    require(value.state == "published" && value.name == grant.edition && value.name.length in 1..48)
    if (value.renderer != "pbr-glb-v1") throw BadgeClientException("UNSUPPORTED_BADGE_RENDERER")
    val p=value.presentation
    require(p.version == 1 && p.materialPolicy == "preserve-gltf" && p.labelStyle == "serial")
    require(p.orientation.size == 4 && p.orientation.all { it.isFinite() } &&
        kotlin.math.abs(kotlin.math.sqrt(p.orientation.sumOf { it * it }) - 1) < .00001)
    require(p.occupancy in .35.. .88 && p.maxZoom in 1.0..4.0 && p.anchors.size <= 2)
    require(p.anchors.keys.all { it in setOf("serial", "recipientPublicId") })
    for (a in p.anchors.values) {
        require(listOf(a.position,a.normal,a.up).all { it.size == 3 && it.all(Double::isFinite) })
        require(a.position.all { kotlin.math.abs(it) <= 10000 } && a.width > 0 && a.width <= 10000 && a.height > 0 && a.height <= 10000)
        val n=a.normal;val u=a.up
        require(kotlin.math.sqrt((n[1]*u[2]-n[2]*u[1]).let { it*it }+(n[2]*u[0]-n[0]*u[2]).let { it*it }+(n[0]*u[1]-n[1]*u[0]).let { it*it }) > .01)
    }
}
