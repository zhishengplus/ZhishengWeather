package com.zhisheng.weather.ui.game

import kotlin.math.min
import kotlin.random.Random

internal enum class SkyBirdPhase { READY, FLYING, PAUSED, LANDED }
internal data class CloudGate(val id: Int, val x: Float, val centerY: Float,
    val passed: Boolean = false, val gap: Float = 122f)
internal data class SkyBirdState(
    val phase: SkyBirdPhase = SkyBirdPhase.READY,
    val height: Float = 520f,
    val birdY: Float = height * .46f,
    val velocity: Float = 0f,
    val distance: Float = 0f,
    val score: Int = 0,
    val gates: List<CloudGate> = listOf(CloudGate(0, 440f, height * .46f)),
    val randomState: Int = 1,
    val celebration: SkyBirdCelebration? = null,
)

/** Each run has a fresh seed; replayable physics and generation do not depend on refresh rate. */
internal object SkyBirdEngine {
    const val WIDTH = 360f
    const val BIRD_X = 92f
    const val RADIUS = 10f
    const val GATE_WIDTH = 60f
    const val FLOOR = 32f
    const val GRAVITY = 1100f
    const val FLAP = -330f
    const val MAX_FALL = 420f
    fun gap(height: Float, score: Int = 0) = ((height * .235f).coerceIn(88f, 122f) -
        min(score * .7f, 16f)).coerceAtLeast(80f)
    fun start(height: Float = 520f, seed: Int = Random.nextInt(1, Int.MAX_VALUE)): SkyBirdState {
        val h = height.coerceIn(260f, 700f)
        val (gate, nextSeed) = nextGate(CloudGate(-1, WIDTH - 188f, h * .46f), h, 0, seed)
        return SkyBirdState(height = h, gates = listOf(gate), randomState = nextSeed)
    }
    // Park-Miller state belongs to the run. Only spawning consumes randomness, never drawing or frames.
    internal fun nextGate(previous: CloudGate, height: Float, score: Int, seed: Int): Pair<CloudGate, Int> {
        var rng = (seed.toLong() and 0x7fffffff).coerceAtLeast(1)
        fun sample(): Float {
            rng = rng * 48271L % 2147483647L
            return (rng.toDouble() / 2147483647.0).toFloat()
        }
        val opening = (gap(height, score) + (sample() - .5f) * 10f).coerceAtLeast(80f)
        val margin = opening / 2f + 30f
        val delta = (height * .22f).coerceIn(60f, 112f)
        val lo = maxOf(margin, previous.centerY - delta)
        val hi = minOf(height - FLOOR - margin, previous.centerY + delta)
        val center = lo + sample() * (hi - lo)
        val spacing = 202f + sample() * 46f
        return CloudGate(previous.id + 1, previous.x + spacing, center, gap = opening) to rng.toInt()
    }
    fun flap(state: SkyBirdState): SkyBirdState = when (state.phase) {
        SkyBirdPhase.READY -> state.copy(phase = SkyBirdPhase.FLYING, velocity = FLAP)
        SkyBirdPhase.FLYING -> state.copy(velocity = FLAP)
        else -> state
    }
    fun pause(state: SkyBirdState) = if (state.phase == SkyBirdPhase.FLYING)
        state.copy(phase = SkyBirdPhase.PAUSED) else state
    fun resume(state: SkyBirdState) = if (state.phase == SkyBirdPhase.PAUSED)
        state.copy(phase = SkyBirdPhase.FLYING) else state
    fun step(state: SkyBirdState, elapsedSeconds: Float): SkyBirdState {
        if (state.phase != SkyBirdPhase.FLYING || !elapsedSeconds.isFinite() || elapsedSeconds <= 0f) return state
        // A delayed frame never teleports through an obstacle. Lifecycle resumes reset the clock.
        var remaining = elapsedSeconds.coerceAtMost(.1f)
        var next = state
        while (remaining > .000001f && next.phase == SkyBirdPhase.FLYING) {
            val dt = min(remaining, 1f / 120f)
            next = tick(next, dt)
            remaining -= dt
        }
        return next
    }
    private fun tick(state: SkyBirdState, dt: Float): SkyBirdState {
        val speed = 155f + min(state.score * 1.5f, 30f)
        val velocity = (state.velocity + GRAVITY * dt).coerceAtMost(MAX_FALL)
        val accelerating = ((MAX_FALL - state.velocity) / GRAVITY).coerceIn(0f, dt)
        val y = state.birdY + state.velocity * accelerating + GRAVITY * accelerating * accelerating / 2f +
            MAX_FALL * (dt - accelerating)
        var gates = state.gates.map { it.copy(x = it.x - speed * dt) }
        val hitsGate = gates.any { gate ->
            val halfGap = gate.gap / 2f
            val nearestX = BIRD_X.coerceIn(gate.x, gate.x + GATE_WIDTH)
            val upperBottom = gate.centerY - halfGap
            val lowerTop = gate.centerY + halfGap
            fun touches(nearestY: Float): Boolean {
                val dx = BIRD_X - nearestX
                val dy = y - nearestY
                return dx * dx + dy * dy <= RADIUS * RADIUS
            }
            touches(y.coerceAtMost(upperBottom)) || touches(y.coerceAtLeast(lowerTop))
        }
        val landed = y - RADIUS <= 0f || y + RADIUS >= state.height - FLOOR || hitsGate
        var score = state.score
        if (!landed) gates = gates.map {
            if (!it.passed && it.x + GATE_WIDTH < BIRD_X - RADIUS) {
                score++; it.copy(passed = true)
            } else it
        }
        val last = gates.last()
        var randomState = state.randomState
        if (last.x < WIDTH - 120f) {
            val (gate, seed) = nextGate(last, state.height, score, randomState)
            gates = gates + gate
            randomState = seed
        }
        val celebration = if (!landed && score / 10 > state.score / 10)
            SkyBirdCelebration(score / 10 * 10, y) else state.celebration?.let {
                it.copy(age = it.age + dt).takeIf { reward -> reward.age < SkyBirdJourney.CELEBRATION_SECONDS }
            }
        return state.copy(phase = if (landed) SkyBirdPhase.LANDED else SkyBirdPhase.FLYING,
            birdY = y.coerceIn(RADIUS, state.height - FLOOR - RADIUS), velocity = velocity,
            distance = state.distance + speed * dt, score = score,
            gates = gates.filter { it.x + GATE_WIDTH > -16f }, randomState = randomState,
            celebration = celebration)
    }
}
