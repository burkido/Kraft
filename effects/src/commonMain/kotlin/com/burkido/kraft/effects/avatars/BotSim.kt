package com.burkido.kraft.effects.avatars

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * The rig (`engine.ts`), line for line in doubles. A pose is a handful of numbers — head yaw,
 * pitch, roll, a position, squash, the eyes — plus blend weights for the three states. A sim
 * advances it: each state sets targets and wanders around them, runs its own events (a flip, a
 * hop, a nod, a blink), and a state change eases from one set of targets to the next.
 */

private val States = BotAvatarState.entries

/* The working state's hop: its period, and the height of the spinning one. */
private const val HopT = 0.68
private const val HopSpinH = 26.0

/* A flip is that spinning hop on its own: a crouch, the hop, the landing's recovery. */
private const val FlipPre = 0.2

/** The jump's numbers: an idle flip's and a click's (`JumpConfig`). */
data class BotJumpConfig(
    /** How high, in body units (the body is 100 tall). */
    val height: Double = HopSpinH,
    /** Seconds in the air. */
    val time: Double = HopT,
    /** How much the body stretches in the air, 0–2. */
    val stretch: Double = 1.0,
    /** How much it squashes on the ground, before take-off and on landing, 0–2. */
    val squash: Double = 1.15,
    /** Seconds the landing squash takes, contact to recovered. */
    val squashTime: Double = 0.37,
    val squashEase: BotSquashEase = BotSquashEase.Pulse,
    /** Seconds the body holds its deepest squash on the ground. */
    val groundTime: Double = 0.11,
    val groundEase: BotSquashEase = BotSquashEase.Pulse,
    /** Seconds the body takes to rise from its deepest squash back to shape. */
    val riseTime: Double = 0.33,
    val riseEase: BotSquashEase = BotSquashEase.Pulse,
    /** Seconds a click's jump takes for its crouch and landing squash. */
    val clickSquashTime: Double = 0.24,
    /** Whole turns in the air. */
    val spin: Double = 1.0,
    /** Degrees of lean into it. */
    val lean: Double = 6.0,
    /** Seconds between idle jumps, roughly (±40 %); 0 for none. */
    val every: Double = 8.0,
    /** When the landing squash begins: seconds before (negative) or after touch-down. */
    val land: Double = 0.0,
)

private fun BotSquashEase.peak(): Double = when (this) {
    BotSquashEase.Sharp -> 0.0
    BotSquashEase.Pulse -> 2.0 / 7
    BotSquashEase.Soft -> 0.5
    BotSquashEase.Bouncy -> 0.144
}

private fun flipPre(j: BotJumpConfig, poked: Boolean) = if (poked) j.clickSquashTime else FlipPre * j.time

private fun flipDuration(j: BotJumpConfig, poked: Boolean) =
    flipPre(j, poked) + j.time + j.squashEase.peak() * (if (poked) j.clickSquashTime else j.squashTime) +
        max(0.0, j.groundTime) + j.riseTime + max(0.0, j.land) + 0.05

/* The rise: how the body comes back from its deepest squash to its shape, 1 → 0. */
private fun risePulse(v: Double, ease: BotSquashEase): Double {
    if (v <= 0) return 1.0
    if (v >= 1) return 0.0
    return when (ease) {
        BotSquashEase.Sharp -> (1 - v) * (1 - v)
        BotSquashEase.Soft -> 0.5 + 0.5 * cos(PI * v)
        BotSquashEase.Bouncy -> exp(-3.2 * v) * cos(5.4 * v) - v * v * v * 0.026
        BotSquashEase.Pulse -> {
            val k = 4.2 * v
            (1 + k) * exp(-k) - v * v * v * 0.078
        }
    }
}

/* The weight settling through the hold, as a gain on the deepest squash. */
private fun groundShape(v: Double, ease: BotSquashEase) = 1 + 0.25 * squashPulse(v, ease)

/* The landing squash over its time, 0..1 → its depth, 0..1 at the peak. */
private fun squashPulse(u: Double, ease: BotSquashEase): Double {
    if (u <= 0 || u >= 1) return 0.0
    val x = when (ease) {
        BotSquashEase.Sharp -> (1 - u) * (1 - u)
        BotSquashEase.Soft -> sin(PI * u).pow(2)
        BotSquashEase.Bouncy -> exp(-3.15 * u) * sin(8.43 * u) / 0.596
        BotSquashEase.Pulse -> {
            val k = 7 * u
            k * k * exp(2 - k) / 4
        }
    }
    val tail = if (u > 0.85) 1 - (u - 0.85) / 0.15 else 1.0
    return x * tail * tail * (3 - 2 * tail)
}

/* The hop's shaping: squash on the ground, stretch at the top. */
private fun hopSquash(a: Double) = exp(-(min(abs(a), abs(a - 1)) / 0.11).pow(2))

/** One frame of the rig (`Pose`). */
class BotPose {
    /** Radians; yaw > 0 turns the face to the viewer's right, pitch > 0 looks up. */
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0

    /** Body-box units (the 100 × 100 design space). */
    var x = 0.0
    var y = 0.0
    var sx = 1.0
    var sy = 1.0

    /** 0 shut … 1 open, before blinks. */
    var eyeOpen = 1.0

    /** How far each lid is down right now, 0 … 1. */
    var blinkL = 0.0
    var blinkR = 0.0
    var lookX = 0.0
    var lookY = 0.0

    /** The breathing cycle, −1 … 1. */
    var breath = 0.0

    /** Working only: how far the eyes have closed into a laugh, 0 … 1. */
    var laugh = 0.0

    /** The whirl round a spinning body: strength 0 … 1, and its head's angle round the ring. */
    var whirl = 0.0
    var whirlAngle = 0.0

    /** Blend weights: default, working, sleeping — they sum to 1. */
    val w = doubleArrayOf(1.0, 0.0, 0.0)

    fun copyFrom(o: BotPose) {
        yaw = o.yaw; pitch = o.pitch; roll = o.roll; x = o.x; y = o.y; sx = o.sx; sy = o.sy
        eyeOpen = o.eyeOpen; blinkL = o.blinkL; blinkR = o.blinkR; lookX = o.lookX; lookY = o.lookY
        breath = o.breath; laugh = o.laugh; whirl = o.whirl; whirlAngle = o.whirlAngle
        w[0] = o.w[0]; w[1] = o.w[1]; w[2] = o.w[2]
    }

    companion object {
        /** The still pose of a state (`restPose`): reduced motion and the first paint. */
        fun rest(state: BotAvatarState): BotPose = BotPose().apply {
            val r = rest[state.ordinal]
            pitch = r.pitch; roll = r.roll; y = r.y; lookX = r.lookX; lookY = r.lookY
            for (i in 0 until 3) w[i] = if (States[i] == state) 1.0 else 0.0
        }
    }
}

private const val Deg = PI / 180
private const val Tau = PI * 2

/* How long a state change takes: settling back to idle is slow, getting to work brisk. */
private fun switchTo(s: BotAvatarState) = when (s) {
    BotAvatarState.Default -> 1.2
    BotAvatarState.Working -> 0.7
    BotAvatarState.Sleeping -> 1.4
}

private const val SwitchFromSleep = 1.0

/** Deterministic per-instance randomness (mulberry32), bit-exact with the web's. */
private class Mulberry(seed: Long) {
    private var a: Int = (seed * 0x9e3779b1L).toInt().let { if (it == 0) 1 else it }

    fun next(): Double {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

/** Exponential approach: `rate` per second, frame-rate independent. */
private fun approach(cur: Double, target: Double, rate: Double, dt: Double) = cur + (target - cur) * (1 - exp(-rate * dt))

private fun easeInOut(p: Double) = if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2

/* Gentler at both ends than the cubic: for drifting off and waking. */
private fun easeSine(p: Double) = 0.5 - 0.5 * cos(PI * p)

/*
 * A channel that picks a new target now and then and moves to it: the head channels as a
 * lightly damped spring, the eyes with an exponential approach.
 */
private class Wander(
    private val rand: Mulberry,
    var amp: Double,
    var holdMin: Double,
    var holdMax: Double,
    var rate: Double,
    private val spring: Boolean = false,
) {
    var value = 0.0
    private var vel = 0.0
    private var target = 0.0
    private var next = 0.0

    fun update(t: Double, dt: Double) {
        if (t >= next) {
            target = (rand.next() * 2 - 1) * amp
            next = t + holdMin + rand.next() * (holdMax - holdMin)
        }
        if (spring) {
            val w = rate * 1.6
            val z = 0.9
            vel += (w * w * (target - value) - 2 * z * w * vel) * dt
            value += vel * dt
        } else {
            value = approach(value, target, rate, dt)
        }
    }

    /** Aim at a value and stay there: the rig picks the next one. */
    fun aim(v: Double) {
        target = v
        next = Double.POSITIVE_INFINITY
    }

    fun set(amp: Double, holdMin: Double, holdMax: Double, rate: Double) {
        this.amp = amp
        this.holdMin = holdMin
        this.holdMax = holdMax
        this.rate = rate
        next = 0.0
    }
}

/* A one-shot event: progress 0 → 1 over its duration, then idle at −1. */
private class BotEvent(var duration: Double) {
    var p = -1.0
    fun fire() {
        p = 0.0
    }
    val active: Boolean get() = p >= 0
    fun update(dt: Double) {
        if (p < 0) return
        p += dt / duration
        if (p >= 1) p = -1.0
    }
}

/* The idle gaze: how far the head swings to a corner, and how long it stays. */
private const val GazeYaw = 35 * Deg
private const val GazePitch = 14 * Deg
private const val GazeRoll = 3.2 * Deg
private const val GazeHoldMin = 2.6
private const val GazeHoldMax = 4.4

private class Rest(val pitch: Double, val roll: Double, val y: Double, val lookX: Double, val lookY: Double)

private val rest = arrayOf(
    Rest(0.0, 0.0, 0.0, 0.0, 0.0),
    Rest(5 * Deg, 0.0, 0.0, 0.0, 0.0),
    Rest(-16 * Deg, 6 * Deg, 3.0, 0.0, 1.0),
)

/** Advances a [BotPose] through time (`Sim`). */
class BotSim(seed: Double, state: BotAvatarState = BotAvatarState.Default) {
    val pose = BotPose()
    var state: BotAvatarState = BotAvatarState.Default
        private set

    private val rand = Mulberry(floor(seed * 1e6).toLong() + 1)
    private var t = 0.0
    private val wFrom = doubleArrayOf(1.0, 0.0, 0.0)
    private var tr = 1.0
    private var trDuration = 1.2
    private val yawW = Wander(rand, 36 * Deg, 1.1, 2.6, 3.0, true)
    private val pitchW = Wander(rand, 10 * Deg, 1.1, 2.6, 2.6, true)
    private val rollW = Wander(rand, 5 * Deg, 1.6, 3.2, 2.0, true)
    private val lookXW = Wander(rand, 3.6, 0.5, 2.0, 14.0)
    private val lookYW = Wander(rand, 2.4, 0.5, 2.0, 14.0)
    private val blink = BotEvent(0.17)
    private var blinkAt: Double
    private var blinkAgain = false
    private val dart = BotEvent(0.12)
    private var dartAt: Double
    private var dartX = 0.0
    private var dartY = 0.0
    private var jump = BotJumpConfig()
    private val flip = BotEvent(flipDuration(jump, false))
    private var flipPoked = false
    private var flipAt: Double
    private var flipSide = 1.0
    private val nod = BotEvent(1.7)
    private var nodAt: Double
    private var hopPhase: Double
    private var hopCount = 0
    private var hopGain = 0.0
    private val laughEv = BotEvent(0.8)
    private var laughAt: Double
    private var prevYaw = 0.0
    private var jelly = 0.0
    private var jellyV = 0.0
    private var gazeLead = 0.0
    private var gazeDirX = 0.0
    private var gazeDirY = 0.0
    private var gazeAt = 0.0
    private var turnK = 1.0
    private var breathPhase: Double
    private var ptrX = 0.0
    private var ptrY = 0.0
    private var ptrS = 0.0
    private var ptrTargetX = 0.0
    private var ptrTargetY = 0.0
    private var ptrTargetS = 0.0
    private var baseYaw = 0.0

    init {
        // Every instance starts somewhere else in its loops.
        t = rand.next() * 10
        hopPhase = rand.next()
        breathPhase = rand.next()
        // Event timers count from that start, so nothing fires on the first tick.
        blinkAt = t + 1 + rand.next() * 3
        flipAt = 0.0
        flipAt = nextFlip(t, 1.0)
        nodAt = t + 3 + rand.next() * 4
        dartAt = t + 1 + rand.next() * 2
        laughAt = t + 0.6 + rand.next() * 1.5
        setState(state, immediate = true)
    }

    fun setState(next: BotAvatarState, immediate: Boolean = false) {
        if (next == state && !immediate) return
        val from = state
        state = next
        val w = pose.w
        if (immediate) {
            for (i in 0 until 3) w[i] = if (States[i] == next) 1.0 else 0.0
            tr = 1.0
        } else {
            wFrom[0] = w[0]; wFrom[1] = w[1]; wFrom[2] = w[2]
            tr = 0.0
            trDuration = if (from == BotAvatarState.Sleeping) SwitchFromSleep else switchTo(next)
        }
        when (next) {
            BotAvatarState.Default -> {
                yawW.set(GazeYaw, 2.6, 5.4, 2.0)
                pitchW.set(GazePitch, 2.8, 5.8, 1.8)
                rollW.set(GazeRoll, 3.4, 6.6, 1.5)
                gazeAt = 0.0
                gazeDirX = 0.0
                gazeDirY = 0.0
                lookXW.set(3.6, 0.6, 2.2, 13.0)
                lookYW.set(2.4, 0.6, 2.2, 13.0)
                flipAt = nextFlip(t, 0.6)
            }
            BotAvatarState.Working -> {
                yawW.set(16 * Deg, 0.9, 1.8, 4.0)
                pitchW.set(3 * Deg, 1.2, 2.4, 3.0)
                rollW.set(0.0, 1.0, 2.0, 3.0)
                lookXW.set(2.0, 0.5, 1.2, 12.0)
                lookYW.set(1.0, 0.5, 1.2, 12.0)
                hopPhase = 0.0
                hopCount = 0
                laughAt = t + 0.5 + rand.next() * 1.2
            }
            BotAvatarState.Sleeping -> {
                yawW.set(7 * Deg, 3.0, 6.0, 0.7)
                pitchW.set(3 * Deg, 3.0, 6.0, 0.7)
                rollW.set(2 * Deg, 3.0, 6.0, 0.6)
                lookXW.set(0.0, 2.0, 4.0, 2.0)
                lookYW.set(0.0, 2.0, 4.0, 2.0)
                nodAt = t + 2.5 + rand.next() * 4
            }
        }
    }

    /** Where a pointer is, relative to the head (−1 … 1 across a head width), and how strongly to follow it. */
    fun setPointer(x: Double, y: Double, strength: Double) {
        ptrTargetX = x.coerceIn(-1.2, 1.2)
        ptrTargetY = y.coerceIn(-1.2, 1.2)
        ptrTargetS = strength.coerceIn(0.0, 1.0)
    }

    /** A hop and a full turn, right now, whatever the state. */
    fun poke() {
        if (flip.active && flip.p < 0.6) return
        flipPoked = true
        flip.duration = flipDuration(jump, true)
        flipSide = if (rand.next() < 0.5) -1.0 else 1.0
        flip.fire()
        flipAt = nextFlip(t, 1.1)
    }

    /** How far the head turns to the side while idle: 1 as the gaze has it, 0 faces forward. */
    fun setTurn(k: Double) {
        val next = max(0.0, k)
        if (next == turnK) return
        turnK = next
        if (state == BotAvatarState.Default) gazeAt = 0.0
    }

    fun setJump(j: BotJumpConfig) {
        val every = jump.every
        jump = j
        if (j.every != every) flipAt = nextFlip(t, 1.0)
    }

    /* Where the head looks next: from a corner mostly straight across to the opposite one. */
    private fun nextGaze() {
        val px = gazeDirX
        val py = gazeDirY
        if (px != 0.0 || py != 0.0) {
            val p = rand.next()
            when {
                p < 0.66 -> { gazeDirX = -px; gazeDirY = -py }
                p < 0.85 -> { gazeDirX = -px; gazeDirY = py }
                else -> { gazeDirX = 0.0; gazeDirY = 0.0 }
            }
            return
        }
        when (floor(rand.next() * 4).toInt()) {
            0 -> { gazeDirX = 1.0; gazeDirY = -1.0 }
            1 -> { gazeDirX = -1.0; gazeDirY = 1.0 }
            2 -> { gazeDirX = -1.0; gazeDirY = -1.0 }
            else -> { gazeDirX = 1.0; gazeDirY = 1.0 }
        }
    }

    /** When the next idle jump is due: `every` seconds, give or take 40 %. */
    private fun nextFlip(t: Double, k: Double): Double {
        val every = jump.every
        return if (every > 0) t + every * k * (0.625 + rand.next() * 0.75) else Double.POSITIVE_INFINITY
    }

    private fun smooth(a: Double, b: Double, v: Double): Double {
        val x = ((v - a) / (b - a)).coerceIn(0.0, 1.0)
        return x * x * (3 - 2 * x)
    }

    private fun envelope(q: Double) = smooth(0.1, 0.26, q) * (1 - smooth(0.66, 0.9, q))
    private fun ringAngle(q: Double) = Tau * (1.5 * q + 0.9 * easeInOut(q))

    /** Advances by [dt0] seconds (already scaled by the speed). */
    fun update(dt0: Double) {
        val dt = min(dt0, 0.05)
        t += dt
        val t = t
        val p = pose
        val w = p.w

        // The state change eases from the weights it started with on a sine S-curve.
        if (tr < 1) {
            tr = min(1.0, tr + dt / trDuration)
            val e = easeSine(tr)
            for (i in 0 until 3) {
                val target = if (States[i] == state) 1.0 else 0.0
                w[i] = wFrom[i] + (target - wFrom[i]) * e
            }
        }
        val wd = w[0]
        val ww = w[1]
        val ws = w[2]

        // Rest targets, blended.
        var restPitch = 0.0
        var restRoll = 0.0
        var restY = 0.0
        var restLookX = 0.0
        var restLookY = 0.0
        for (i in 0 until 3) {
            val r = rest[i]
            restPitch += r.pitch * w[i]
            restRoll += r.roll * w[i]
            restY += r.y * w[i]
            restLookX += r.lookX * w[i]
            restLookY += r.lookY * w[i]
        }

        // The idle gaze: a place to look, a while to stay, then the swing to the next.
        if (state == BotAvatarState.Default && t >= gazeAt) {
            nextGaze()
            val reach = 0.84 + rand.next() * 0.16
            yawW.aim(gazeDirX * GazeYaw * reach * turnK)
            pitchW.aim(gazeDirY * GazePitch * reach)
            rollW.aim(gazeDirX * GazeRoll * reach * turnK)
            gazeAt = t + GazeHoldMin + rand.next() * (GazeHoldMax - GazeHoldMin)
        }

        yawW.update(t, dt)
        pitchW.update(t, dt)
        rollW.update(t, dt)
        lookXW.update(t, dt)
        lookYW.update(t, dt)

        // Following the pointer: the eyes lead, the head turns after them, the wander quietens.
        ptrS = approach(ptrS, ptrTargetS, 8.0, dt)
        ptrX = approach(ptrX, ptrTargetX, 14.0, dt)
        ptrY = approach(ptrY, ptrTargetY, 14.0, dt)
        val ps = ptrS
        val quiet = 1 - 0.75 * ps

        baseYaw = approach(baseYaw, yawW.value * quiet + 22 * Deg * ptrX * ps, 5.0, dt)
        val basePitch = restPitch + pitchW.value * quiet - 12 * Deg * ptrY * ps
        val baseRoll = restRoll + rollW.value * quiet
        val baseY = restY
        val baseLookX = restLookX + lookXW.value * quiet + 4.5 * ptrX * ps
        val baseLookY = restLookY + lookYW.value * quiet + 3 * ptrY * ps

        var spin = 0.0
        var hopY = 0.0
        var sx = 1.0
        var sy = 1.0
        var pitchAdd = 0.0
        var rollAdd = 0.0
        var blinkClose = 0.0
        var lookXAdd = 0.0
        var lookYAdd = 0.0
        var laugh = 0.0
        var whirl = 0.0
        var whirlAngle = 0.0

        // Blinks: idle and working blink; a double blink now and then.
        if (t >= blinkAt && !blink.active && wd + ww > 0.5) {
            blink.fire()
            blinkAgain = !blinkAgain && rand.next() < 0.22
            blinkAt = t + if (blinkAgain) 0.28 else 2.2 + rand.next() * 2.6
        }
        blink.update(dt)
        if (blink.active) blinkClose = sin(PI * blink.p)

        // Eye darts: a quick glance to the side and back.
        if (t >= dartAt && !dart.active && wd + ww > 0.5) {
            dart.fire()
            dartX = (rand.next() * 2 - 1) * 4
            dartY = (rand.next() * 2 - 1) * 2
            dart.duration = 0.25 + rand.next() * 0.45
            dartAt = t + 1.2 + rand.next() * 2.6
        }
        dart.update(dt)
        if (dart.active) {
            val q = dart.p
            val hold = if (q < 0.15) q / 0.15 else if (q > 0.8) (1 - q) / 0.2 else 1.0
            lookXAdd += dartX * hold * (wd + ww)
            lookYAdd += dartY * hold * (wd + ww)
        }

        // Idle: a full turn now and then, with a jump.
        if (state == BotAvatarState.Default && t >= flipAt && !flip.active) {
            flipPoked = false
            flip.duration = flipDuration(jump, false)
            flipSide = if (rand.next() < 0.5) -1.0 else 1.0
            flip.fire()
            flipAt = nextFlip(t, 1.0)
        }
        flip.update(dt)
        if (flip.active) {
            val j = jump
            val preS = flipPre(j, flipPoked)
            val a = (flip.p * flip.duration - preS) / j.time
            val q = a.coerceIn(0.0, 1.0)
            val arc = sin(PI * q)
            spin += Tau * j.spin * easeInOut(q)
            hopY -= j.height * arc
            val tl = (a - 1) * j.time - j.land
            val squashTime = if (flipPoked) j.clickSquashTime else j.squashTime
            val hold = max(0.0, j.groundTime)
            val peakT = j.squashEase.peak() * squashTime
            val rise = tl - peakT - hold
            val depth = when {
                tl <= peakT -> squashPulse(tl / squashTime, j.squashEase)
                rise <= 0 -> groundShape((tl - peakT) / hold, j.groundEase)
                else -> risePulse(rise / j.riseTime, j.riseEase)
            }
            val crouch = { u: Double -> if (flipPoked) u * u * (3 - 2 * u) else hopSquash(u - 1) }
            val land = (
                if (a < 0) crouch(max(0.0, 1 + a * j.time / preS))
                else if (tl > 0) depth
                else if (a < 0.2) hopSquash(a)
                else 0.0
                ) * j.squash
            sx += 0.16 * land - 0.06 * arc * j.stretch
            sy += -0.18 * land + 0.09 * arc * j.stretch
            rollAdd += flipSide * j.lean * Deg * arc
            if (j.spin > 0) {
                laugh = max(laugh, arc)
                whirl = max(whirl, envelope(q))
                whirlAngle = ringAngle(q)
            }
        }

        // Working: hops all the time, every third one spins; leaving, the hop under way lands whole.
        val j = jump
        val exitHold = max(0.0, j.groundTime)
        val exitFor = exitHold + j.riseTime
        if (state == BotAvatarState.Working) hopGain = ww
        if (hopGain > 0.02 && (state == BotAvatarState.Working || hopPhase > 0)) {
            hopPhase += dt / HopT
            if (hopPhase >= 1) {
                if (state == BotAvatarState.Working) {
                    hopPhase -= 1
                    hopCount += 1
                } else if ((hopPhase - 1) * HopT >= exitFor) {
                    hopPhase = 0.0
                    hopGain = 0.0
                }
            }
            val g = hopGain
            val q = min(1.0, hopPhase)
            val arc = sin(PI * q)
            val spinning = hopCount % 3 == 2
            val h = if (spinning) HopSpinH else 18.0
            hopY -= h * arc * g
            val exitT = if (state != BotAvatarState.Working && hopPhase > 1) (hopPhase - 1) * HopT else -1.0
            val land = when {
                exitT < 0 -> hopSquash(hopPhase)
                exitT < exitHold -> groundShape(exitT / exitHold, j.groundEase)
                else -> risePulse((exitT - exitHold) / j.riseTime, j.riseEase)
            }
            sx += (0.16 * land - 0.06 * arc) * g
            sy += (-0.18 * land + 0.09 * arc) * g
            if (spinning) {
                spin += Tau * easeInOut(q) * g
                laugh = max(laugh, arc * g)
                if (envelope(q) * g > whirl) {
                    whirl = envelope(q) * g
                    whirlAngle = ringAngle(q)
                }
            }
            rollAdd += (if (hopCount % 2 == 0) 1 else -1) * 6 * Deg * arc * g
        }

        // Working: now and then a laugh shuts the eyes into arcs.
        if (state == BotAvatarState.Working && t >= laughAt && !laughEv.active) {
            laughEv.fire()
            laughEv.duration = 0.6 + rand.next() * 0.5
            laughAt = t + 1.6 + rand.next() * 2.2
        }
        laughEv.update(dt)
        if (laughEv.active) {
            val q = laughEv.p
            laugh = max(laugh, if (q < 0.18) q / 0.18 else if (q > 0.78) (1 - q) / 0.22 else 1.0)
        }

        // Sleeping: the head drops, then jerks back up.
        if (state == BotAvatarState.Sleeping && t >= nodAt && !nod.active) {
            nod.fire()
            nodAt = t + 4 + rand.next() * 4
        }
        nod.update(dt)
        if (nod.active) {
            val q = nod.p
            val dip = if (q < 0.72) easeInOut(q / 0.72) else 1 - easeInOut((q - 0.72) / 0.28)
            pitchAdd -= 13 * Deg * dip * ws
        }

        // Breathing, always; deeper and slower asleep. The phase is integrated, so it never jumps.
        breathPhase += dt / (3.6 + 1.2 * ws)
        val breath = sin(breathPhase * Tau)
        p.breath = breath
        sx += breath * (0.008 + 0.014 * ws)
        sy += breath * (0.012 + 0.02 * ws)
        val bob = sin(t * Tau / 3.4) * 2 * (1 - ws)

        p.yaw = baseYaw + spin

        // The jelly: the faster the head turns, the more the body stretches along the turn.
        var dyaw = baseYaw - prevYaw
        dyaw = ((dyaw + PI) % Tau + Tau) % Tau - PI
        prevYaw = baseYaw
        val rate = if (dt > 0) abs(dyaw) / dt else 0.0
        val leadTarget = if (dt > 0) (dyaw / dt * 2.4).coerceIn(-2.2, 2.2) else 0.0
        gazeLead = approach(gazeLead, leadTarget, 9.0, dt)
        val jellyTarget = min(0.22, 0.055 * rate)
        val omega = 16.0
        val zeta = 0.45
        jellyV += (omega * omega * (jellyTarget - jelly) - 2 * zeta * omega * jellyV) * dt
        jelly += jellyV * dt
        val jel = jelly.coerceIn(-0.08, 0.28) * 0.6
        sx *= 1 + jel
        sy *= 1 - 0.55 * jel

        p.pitch = basePitch + pitchAdd
        p.roll = baseRoll + rollAdd
        p.x = 0.0
        p.y = baseY + hopY + bob
        p.sx = sx
        p.sy = sy
        p.eyeOpen = 1.0
        p.laugh = approach(p.laugh, laugh, 30.0, dt)
        p.blinkL = blinkClose
        p.blinkR = blinkClose
        p.lookX = baseLookX + lookXAdd + gazeLead
        p.lookY = baseLookY + lookYAdd
        p.whirl = whirl
        p.whirlAngle = whirlAngle
    }
}
