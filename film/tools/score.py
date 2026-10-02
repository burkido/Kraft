"""
The film's score and sound design, synthesised from scratch (no samples, nothing to license).

96 BPM in D major: one bar is 2.5 s, so every 5 s chapter is exactly two bars and every cut
lands on a downbeat. Sound effects are placed at the frame times the shots use (see the Kotlin
shots for the matching numbers).

    python film/tools/score.py film/build/audio
"""
import os
import sys

import numpy as np
import soundfile as sf
from scipy.signal import butter, fftconvolve, sosfilt, sosfilt_zi

SR = 48_000
BPM = 96
BEAT = 60 / BPM          # 0.625 s
BAR = 4 * BEAT           # 2.5 s
LENGTH = 70.0
N = int(LENGTH * SR)
rng = np.random.default_rng(7)

# Edit points (s) — must match the shot list.
TITLE, CH = 7.5, [12.5 + 5 * i for i in range(7)]
ORBS, BEAM, METAL, GOOEY, VOICE, BOTS, IMAGE = CH
CODE, PLATFORMS, FINALE, END = 47.5, 52.5, 57.5, 62.5
VOICE_AT = VOICE + 0.55


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def note(name):
    names = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
    return names[name[:-1]] + 12 * (int(name[-1]) + 1)


def T(sec):
    return int(round(sec * SR))


def buf():
    return np.zeros((N, 2))


def add(bus, start, sig, gain=1.0, pan=0.0):
    """Mixes a mono or stereo signal into [bus] at [start] s with an equal-power pan."""
    i = T(start)
    if i >= N:
        return
    tail = min(len(sig), T(0.03))
    sig = sig.copy()
    fade = np.linspace(1, 0, tail) ** 2
    sig[-tail:] *= fade if sig.ndim == 1 else fade[:, None]
    if sig.ndim == 1:
        l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
        sig = np.stack([sig * l * 1.4142, sig * r * 1.4142], 1)
    j = min(N, i + len(sig))
    if i < 0:
        sig = sig[-i:]
        i = 0
    bus[i:j] += sig[: j - i] * gain


def env(n, a, d, s, r, total):
    """ADSR over [total] s with times in s; returns length n."""
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-4), 1.0)
    e = np.where((t >= a) & (t < a + d), 1 - (1 - s) * (t - a) / max(d, 1e-4), e)
    e = np.where((t >= a + d), s, e)
    rel = total - r
    e = np.where(t >= rel, e * np.clip(1 - (t - rel) / max(r, 1e-4), 0, 1), e)
    return e


def lp(x, f, order=2):
    return sosfilt(butter(order, min(f, SR / 2.2), "low", fs=SR, output="sos"), x, axis=0)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, "high", fs=SR, output="sos"), x, axis=0)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, min(hi, SR / 2.2)], "band", fs=SR, output="sos"), x, axis=0)


def sweep(x, f0, f1, kind="low", block=512):
    """A filter whose cutoff glides exponentially from f0 to f1 over the signal."""
    out = np.zeros_like(x)
    nb = max(1, len(x) // block)
    zi = None
    for b in range(nb + 1):
        s, e = b * block, min(len(x), (b + 1) * block)
        if s >= e:
            break
        f = f0 * (f1 / f0) ** (b / nb)
        sos = butter(2, min(f, SR / 2.3), kind, fs=SR, output="sos")
        if zi is None:
            zi = sosfilt_zi(sos) * 0
            if x.ndim == 2:
                zi = np.repeat(zi[:, :, None], x.shape[1], axis=2)
        out[s:e], zi = sosfilt(sos, x[s:e], axis=0, zi=zi)
    return out


def compress(x, threshold_db=-24, ratio=4.0, attack=0.005, release=0.09, makeup_db=8):
    """A feed-forward compressor with a smoothed peak detector (mono)."""
    a = np.exp(-1 / (attack * SR))
    r = np.exp(-1 / (release * SR))
    level = np.abs(x)
    envl = np.zeros_like(x)
    e = 0.0
    for i, v in enumerate(level):
        e = a * e + (1 - a) * v if v > e else r * e + (1 - r) * v
        envl[i] = e
    db = 20 * np.log10(envl + 1e-9)
    over = np.maximum(0, db - threshold_db)
    gain = 10 ** ((-over * (1 - 1 / ratio) + makeup_db) / 20)
    return x * gain


def reverb_ir(seconds=3.8, bright=5000, seed=1):
    r = np.random.default_rng(seed)
    n = T(seconds)
    t = np.arange(n) / SR
    tau = seconds / 6.9
    ir = np.stack([r.standard_normal(n), r.standard_normal(n)], 1) * np.exp(-t / tau)[:, None]
    dark = lp(ir, 1200) * np.exp(-t / (tau * 1.3))[:, None]
    ir = lp(ir, bright) * np.exp(-t / (tau * 0.5))[:, None] + dark
    ir[: T(0.012)] *= np.linspace(0, 1, T(0.012))[:, None]  # pre-delay feel
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x, ir, mix):
    wet = np.stack([fftconvolve(x[:, 0], ir[:, 0])[:N], fftconvolve(x[:, 1], ir[:, 1])[:N]], 1)
    return x * (1 - mix) + wet * mix * 3.0


def pingpong(x, delay=BEAT * 0.75, fb=0.38, wet=0.32, damp=3500):
    d = T(delay)
    out = x.copy()
    tap = x.copy()
    for k in range(1, 7):
        tap = lp(tap, damp) * fb
        shifted = np.zeros_like(x)
        shifted[d * k:] = tap[: N - d * k][:, ::-1] if k % 2 else tap[: N - d * k]
        out += shifted * wet
    return out


# ── Instruments ────────────────────────────────────────────────────────────────────────────

def saw(f, n, detune_cents=0.0, harmonics_to=7000):
    t = np.arange(n) / SR
    f = f * 2 ** (detune_cents / 1200)
    x = np.zeros(n)
    for k in range(1, max(2, int(harmonics_to / f))):
        x += np.sin(2 * np.pi * f * k * t + rng.uniform(0, 6.28)) / k
    return x


def pad_chord(notes, dur, cutoff=1800, attack=1.6, release=2.2, level=0.10):
    n = T(dur + release)
    out = np.zeros((n, 2))
    for m in notes:
        f = hz(m)
        l = saw(f, n, -6) + saw(f, n, +4) * 0.8
        r = saw(f, n, +6) + saw(f, n, -3) * 0.8
        sine = np.sin(2 * np.pi * f * np.arange(n) / SR) * 1.5
        out += np.stack([l + sine, r + sine], 1)
    out = lp(out, cutoff, 2)
    out *= env(n, attack, 0.5, 0.85, release, dur + release)[:, None]
    return out * level / max(1, len(notes)) * 1.8


def pluck(m, dur=1.6, bright=1.0, level=0.22):
    """A soft FM mallet: warm body, a glassy transient."""
    n = T(dur)
    t = np.arange(n) / SR
    f = hz(m)
    idx = 2.2 * bright * np.exp(-t / 0.08)
    x = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * 2 * t))
    x += 0.35 * np.sin(2 * np.pi * f * 4.01 * t) * np.exp(-t / 0.05)
    x *= np.exp(-t / (0.45 + 120 / f * 0.1)) * np.clip(t / 0.003, 0, 1)
    return x * level


def bell(m, dur=2.6, level=0.12):
    n = T(dur)
    t = np.arange(n) / SR
    f = hz(m)
    x = np.sin(2 * np.pi * f * t + 1.1 * np.exp(-t / 0.35) * np.sin(2 * np.pi * f * 3.0 * t))
    return x * np.exp(-t / 0.9) * np.clip(t / 0.002, 0, 1) * level


def sub(m, dur, level=0.3):
    n = T(dur)
    t = np.arange(n) / SR
    f = hz(m)
    x = np.sin(2 * np.pi * f * t) + 0.2 * np.sin(2 * np.pi * 2 * f * t)
    return x * env(n, 0.02, 0.3, 0.7, 0.25, dur) * level


def kick(level=0.55):
    n = T(0.5)
    t = np.arange(n) / SR
    f = 44 + 90 * np.exp(-t / 0.035)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.22)
    x += lp(rng.standard_normal(n), 2500) * np.exp(-t / 0.006) * 0.25
    return x * level


def hat(level=0.05):
    n = T(0.09)
    t = np.arange(n) / SR
    return hp(rng.standard_normal(n), 7000) * np.exp(-t / 0.018) * level


def impact(level=0.9):
    n = T(3.0)
    t = np.arange(n) / SR
    f = 30 + 55 * np.exp(-t / 0.18)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.85)
    crack = lp(rng.standard_normal(n), 900) * np.exp(-t / 0.09) * 0.6
    air = hp(rng.standard_normal(n), 3000) * np.exp(-t / 0.5) * 0.05
    return (boom + crack + air) * level


def riser(dur=2.5, level=0.22):
    n = T(dur)
    t = np.arange(n) / SR
    x = sweep(rng.standard_normal(n), 300, 9000, "low")
    x = hp(x, 200)
    return x * (t / dur) ** 2.2 * level


def whoosh(dur=0.9, lo=250, hi=5000, level=0.10, up=True):
    n = T(dur)
    x = sweep(rng.standard_normal(n), lo if up else hi, hi if up else lo, "low")
    x = hp(x, 150)
    e = np.sin(np.pi * np.linspace(0, 1, n)) ** 1.6
    return x * e * level


def tick(level=0.05, f=1500):
    n = T(0.08)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.010) * 0.6
    x += np.sin(2 * np.pi * 170 * t) * np.exp(-t / 0.025)
    x *= np.clip(t / 0.0015, 0, 1)
    return lp(x, 4000) * level


def bloop(f0=320, level=0.10):
    n = T(0.22)
    t = np.arange(n) / SR
    f = f0 * (1 + 0.9 * (1 - np.exp(-t / 0.03)))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.06) * np.clip(t / 0.004, 0, 1)
    return x * level


def key_click(level=0.035):
    n = T(0.03)
    t = np.arange(n) / SR
    x = bp(rng.standard_normal(n), 1800, 6000) * np.exp(-t / 0.004)
    return x * level * rng.uniform(0.6, 1.0)


# ── Harmony ────────────────────────────────────────────────────────────────────────────────

def ch(*names):
    return [note(x) for x in names]


PROGRESSION = [  # (start, dur, pad voicing, bass root, arp tones)
    (0.0, 7.5, ch("D3", "A3", "E4", "F#4", "C#5"), "D2", None),
    (TITLE, 5.0, ch("D3", "A3", "E4", "F#4", "A4", "C#5"), "D2", None),
    (ORBS, 5.0, ch("B2", "F#3", "A3", "C#4", "D4"), "B1", ch("B3", "D4", "F#4", "A4", "C#5")),
    (BEAM, 5.0, ch("G2", "D3", "F#3", "B3", "C#4"), "G1", ch("G3", "B3", "D4", "F#4", "A4")),
    (METAL, 5.0, ch("D3", "A3", "C#4", "E4", "F#4"), "D2", ch("D4", "E4", "F#4", "A4", "C#5")),
    (GOOEY, 5.0, ch("A2", "E3", "B3", "D4", "F#4"), "A1", ch("A3", "B3", "D4", "E4", "F#4")),
    (VOICE, 5.0, ch("B2", "F#3", "A3", "C#4", "D4"), "B1", ch("B3", "D4", "F#4", "A4", "C#5")),
    (BOTS, 5.0, ch("G2", "D3", "F#3", "A3", "B3"), "G1", ch("G3", "B3", "D4", "F#4", "A4")),
    (IMAGE, 2.5, ch("E3", "B3", "D4", "F#4", "G4"), "E2", ch("E4", "F#4", "G4", "B4", "D5")),
    (IMAGE + 2.5, 2.5, ch("A2", "E3", "G3", "C#4", "F#4"), "A1", ch("A3", "C#4", "E4", "G4", "B4")),
    (CODE, 5.0, ch("G2", "D3", "F#3", "B3", "E4"), "G1", None),
    (PLATFORMS, 5.0, ch("A2", "E3", "A3", "B3", "E4"), "A1", ch("A3", "B3", "E4", "A4", "B4")),
    (FINALE, 5.0, ch("D3", "A3", "C#4", "E4", "F#4", "A4"), "D2", ch("D4", "F#4", "A4", "C#5", "E5")),
    (END, 2.5, ch("G2", "D3", "F#3", "B3", "D4"), "D2", None),
    (END + 2.5, 5.0, ch("D3", "A3", "E4", "F#4", "A4", "E5"), "D2", None),
]

ARP_ORDER = [0, 2, 4, 1, 3, 2, 4, 1]


def intensity(t):
    """0..1 energy curve that shapes the arrangement."""
    pts = [(0, 0.15), (TITLE, 0.35), (ORBS, 0.45), (METAL, 0.6), (GOOEY, 0.65), (VOICE, 0.5), (BOTS, 0.7),
           (IMAGE, 0.8), (CODE, 0.4), (PLATFORMS, 0.7), (FINALE, 1.0), (END, 0.5), (LENGTH, 0.2)]
    xs, ys = zip(*pts)
    return float(np.interp(t, xs, ys))


def build(out_dir, voice_path):
    pads, arps, bass, drums, fx = buf(), buf(), buf(), buf(), buf()

    # Pads and bass follow the progression; the cutoff opens with the energy.
    for start, dur, voicing, root, arp in PROGRESSION:
        e = intensity(start)
        cutoff = 900 + 2600 * e
        if start == CODE:
            cutoff = 700
        add(pads, start, pad_chord(voicing, dur, cutoff=cutoff, attack=1.4 if start else 4.5, level=(0.12 + 0.05 * e) * (0.6 if start == 0 else 1)))
        if start >= ORBS and start < END:
            b = note(root)
            for k in range(int(dur / BEAT)):
                bt = start + k * BEAT
                if k % 2 == 0 or (FINALE <= bt < END):
                    add(bass, bt, sub(b, BEAT * 1.8, level=0.16 + 0.12 * intensity(bt)))
        # Arpeggio: eighths, sixteenths in the finale and platforms build.
        if arp:
            step = BEAT / 2 if start < PLATFORMS else BEAT / 4
            k = 0
            t0 = start
            while t0 < start + dur - 1e-6:
                m = arp[ARP_ORDER[k % len(ARP_ORDER)] % len(arp)]
                if k % 8 == 7 and start >= METAL:
                    m += 12
                lvl = 0.10 + 0.14 * intensity(t0)
                if start == PLATFORMS:
                    lvl *= 0.35 + 0.65 * (t0 - start) / dur
                add(arps, t0, pluck(m, 1.4, bright=0.7 + 0.6 * intensity(t0), level=lvl), pan=0.35 * np.sin(k * 1.7))
                t0 += step
                k += 1

    # Drums: a soft heartbeat from the metal chapter, out for the code breakdown, full in the finale.
    t0 = METAL
    while t0 < END:
        in_code = CODE <= t0 < PLATFORMS
        beat_in_bar = round((t0 - METAL) / BEAT) % 4
        if not in_code:
            if beat_in_bar in (0, 2) or t0 >= FINALE:
                add(drums, t0, kick(0.42 + 0.25 * intensity(t0)))
            if t0 >= GOOEY:
                add(drums, t0 + BEAT / 2, hat(0.035 + 0.03 * intensity(t0)), pan=0.3)
        t0 += BEAT

    # Intro drone, the title hit, and the ending.
    n = T(8.5)
    t = np.arange(n) / SR
    drone = (np.sin(2 * np.pi * hz(note("D1")) * t) + 0.5 * np.sin(2 * np.pi * hz(note("A1")) * t)) * np.clip(t / 5, 0, 1) ** 2 * 0.22
    add(pads, 0.0, drone * np.clip((8.5 - t) / 1.0, 0, 1))
    add(fx, TITLE - 2.4, riser(2.4, 0.13))
    add(fx, TITLE, impact(0.95))
    add(fx, TITLE, bell(note("A5"), 3.5, 0.10), pan=-0.3)
    add(fx, TITLE + 0.06, bell(note("E6"), 3.5, 0.07), pan=0.3)
    add(fx, FINALE - 2.5, riser(2.5, 0.14))
    add(fx, FINALE, impact(0.8))
    add(fx, END, impact(0.6))
    for i, m in enumerate(["D5", "A5", "F#5", "E6"]):
        add(fx, END + 0.25 + i * 0.28, bell(note(m), 4.0, 0.08), pan=-0.4 + i * 0.27)

    # Cuts: a soft tick and an airy whoosh into each chapter.
    for c in CH + [CODE, PLATFORMS]:
        add(fx, c - 0.45, whoosh(0.8, 250, 3500, level=0.045), pan=0.0)
        add(fx, c, tick(0.03, 1400))

    # Opening orb forming, and the line of copy.
    add(fx, 0.5, whoosh(3.0, 120, 2500, 0.05))

    # Metal: the camera pull-back and the press.
    add(fx, METAL + 1.4, whoosh(1.8, 200, 2500, 0.06, up=False))
    add(fx, METAL + 3.75, tick(0.08, 1800))
    add(fx, METAL + 4.45, tick(0.04, 1500))

    # Gooey: droplets split and fold back; the slider grab.
    for i, f0 in enumerate([300, 360, 420]):
        add(fx, GOOEY + 0.55 + i * 0.04, bloop(f0, 0.09), pan=-0.5 + i * 0.5)
    add(fx, GOOEY + 3.05, bloop(260, 0.07))
    add(fx, GOOEY + 1.35, tick(0.045, 1700))
    add(fx, GOOEY + 1.4, whoosh(2.8, 300, 1500, 0.035))

    # Bots: little life.
    add(fx, BOTS + 3.3, bloop(520, 0.06), pan=-0.3)
    add(fx, BOTS + 2.4, bloop(440, 0.05), pan=-0.1)

    # Image reveals: a sparkle each.
    for i, at in enumerate([1.9, 2.35, 2.8]):
        for j, m in enumerate(["A5", "C#6", "E6"]):
            add(fx, IMAGE + at + j * 0.05, bell(note(m), 1.6, 0.022), pan=-0.6 + i * 0.6)
        add(fx, IMAGE + at - 0.15, whoosh(0.5, 1500, 6000, 0.018))

    # Code: key clicks for every character typed.
    snippets = [("ThinkingOrb(\n    state = OrbState.Searching,\n)", 0.35),
                ("BorderBeam(size = BeamSize.Md) {\n    ChatInput()\n}", 1.95),
                ("BotAvatar(\n    type = BotAvatarType.Ghost,\n)", 3.45)]
    for code, at in snippets:
        chars = [c for c in code if not c.isspace()]
        for i in range(0, len(chars), 2):
            add(fx, CODE + at + 0.5 * i / len(chars), key_click(0.05), pan=rng.uniform(-0.2, 0.2))
        add(fx, CODE + at + 0.55, bell(note("B4"), 1.2, 0.025))

    # Platforms: devices rise.
    add(fx, PLATFORMS + 0.2, whoosh(1.4, 150, 2500, 0.05))

    # Voice line, and the music ducking under it.
    voice = np.zeros((N, 2))
    v, vsr = sf.read(voice_path)
    if v.ndim > 1:
        v = v.mean(1)
    if vsr != SR:
        v = np.interp(np.arange(int(len(v) * SR / vsr)) / SR, np.arange(len(v)) / vsr, v)
    v = hp(v, 90)
    v = v - hp(v, 5500, 4) * 0.65  # de-ess: sibilants ~9 dB down
    v = v / np.abs(v).max()
    v = compress(v, threshold_db=-20, ratio=4, makeup_db=6)
    v = v / np.abs(v).max() * 0.5
    add(voice, VOICE_AT, v)
    duck = np.ones(N)
    a, b = T(VOICE_AT - 0.25), T(VOICE_AT + len(v) / SR + 0.4)
    ramp = T(0.3)
    duck[a:b] = 0.35
    duck[a - ramp:a] = np.linspace(1, 0.35, ramp)
    duck[b:b + ramp] = np.linspace(0.35, 1, ramp)

    # Buses → space → master.
    ir_long = reverb_ir(4.2, 5500, 1)
    ir_short = reverb_ir(1.6, 7000, 2)
    pads = hp(pads, 170)
    pads = pads - bp(pads, 220, 480) * 0.45   # ~ -5 dB dip in the mud band
    pads = reverb(pads, ir_long, 0.45)
    arps = reverb(pingpong(arps), ir_long, 0.30)
    bass = lp(bass, 400)
    drums = reverb(drums, ir_short, 0.12)
    fx = reverb(fx, ir_long, 0.25)
    voice = reverb(voice, ir_short, 0.10)

    # The arc: a hush for the cold open, a build through the chapters, a breath for the code,
    # the peak on the finale grid.
    arc_db = [(0, -16), (5.0, -10), (7.4, -6), (7.5, -1), (12.5, -4), (22.5, -3), (32.5, -3), (37.5, -2),
              (45.0, -0.5), (47.5, -6), (52.5, -5), (57.4, -1), (57.5, 1.5), (62.5, 0), (LENGTH, 0)]
    xs, ys = zip(*arc_db)
    arc = 10 ** (np.interp(np.arange(N) / SR, xs, ys) / 20)
    music = (pads + arps + bass + drums) * (duck * arc)[:, None]
    mix = music + fx + voice * 2.2
    mix = hp(mix, 25)
    # Fade in/out.
    fade = np.ones(N)
    fade[: T(0.05)] = np.linspace(0, 1, T(0.05))
    fade[T(LENGTH - 2.0):] = np.linspace(1, 0, N - T(LENGTH - 2.0)) ** 1.5
    mix *= fade[:, None]
    # Soft limiter; final loudness is set at the mux (loudnorm).
    mix = mix / (np.percentile(np.abs(mix), 99.95) + 1e-9) * 0.8
    mix = np.tanh(mix * 1.1) * 0.95

    os.makedirs(out_dir, exist_ok=True)
    sf.write(os.path.join(out_dir, "score.wav"), mix.astype(np.float32), SR, subtype="FLOAT")
    for name, bus in [("stem_music", music), ("stem_fx", fx), ("stem_voice", voice)]:
        sf.write(os.path.join(out_dir, f"{name}.wav"), (bus / (np.abs(bus).max() + 1e-9) * 0.9).astype(np.float32), SR, subtype="FLOAT")
    print("wrote", out_dir)


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    # python score.py [out_dir] [--voice onyx|sage]
    voice = sys.argv[sys.argv.index("--voice") + 1] if "--voice" in sys.argv else "onyx"
    rest = [a for i, a in enumerate(sys.argv[1:], 1) if a != "--voice" and sys.argv[i - 1] != "--voice"]
    build(rest[0] if rest else os.path.join(here, "../build/audio"), os.path.join(here, f"../assets/voice_{voice}.wav"))
