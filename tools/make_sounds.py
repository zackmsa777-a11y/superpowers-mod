"""Synthesizes the mod's custom sound effects and encodes them to mono OGG for Minecraft.

usage: python make_sounds.py <output sounds dir>
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
rng = np.random.default_rng(1337)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def noise(dur):
    return rng.uniform(-1, 1, int(dur * SR))


def env_adsr(n, a, d, s, r, sustain_level=0.7):
    """Envelope with attack/decay/sustain/release given in seconds; sustain fills the remainder."""
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    s_len = max(0, n - a - d - r)
    e = np.concatenate([
        np.linspace(0, 1, max(a, 1)),
        np.linspace(1, sustain_level, max(d, 1)),
        np.full(s_len, sustain_level),
        np.linspace(sustain_level, 0, max(r, 1)),
    ])
    return np.pad(e, (0, max(0, n - len(e))))[:n]


def exp_decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def svf(x, cutoff, q=0.7, mode="lp"):
    """Chamberlin state-variable filter with per-sample cutoff (Hz)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    f = 2 * np.sin(np.pi * np.clip(cutoff, 20, SR / 6) / SR)
    low = band = 0.0
    out = np.empty_like(x)
    damp = 1.0 / q
    for i in range(len(x)):
        low += f[i] * band
        high = x[i] - low - damp * band
        band += f[i] * high
        out[i] = low if mode == "lp" else band if mode == "bp" else high
    return out


def sweep_sine(f0, f1, dur, curve=1.0):
    n = int(dur * SR)
    k = np.linspace(0, 1, n) ** curve
    freq = f0 + (f1 - f0) * k
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def saw(freq, n):
    freq = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    phase = np.cumsum(freq) / SR
    return 2 * (phase - np.floor(phase + 0.5))


def reverb(x, decay=1.2, mix=0.35, length=2.0):
    n = int(length * SR)
    ir = rng.uniform(-1, 1, n) * np.exp(-np.arange(n) / (decay * SR / 6.9))
    ir = svf(ir, 6000) if n < 400000 else ir
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    size = len(x) + n
    nfft = 1 << (size - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[:size]
    dry = np.pad(x, (0, n))
    return dry * (1 - mix) + wet * mix * 1.5


def finish(x, fade=0.05):
    x = np.tanh(x * 1.2)
    f = int(fade * SR)
    if f > 0 and len(x) > f:
        x[-f:] *= np.linspace(1, 0, f)
    # trim trailing silence
    nz = np.nonzero(np.abs(x) > 1e-3)[0]
    if len(nz):
        x = x[: nz[-1] + 1]
    peak = np.max(np.abs(x)) + 1e-9
    return x / peak * 0.93


# ---------------------------------------------------------------- sounds

def s_charge():
    d = 1.6
    t = t_axis(d)
    n = len(t)
    k = t / d
    nz = svf(noise(d), 300 + 5000 * k ** 2, q=3, mode="bp") * 0.6
    tone = sum(np.sin(2 * np.pi * np.cumsum(f * (1 + 1.5 * k ** 2)) / SR) / (i + 1) for i, f in enumerate([110, 165, 220, 330]))
    shimmer = np.sin(2 * np.pi * np.cumsum(1800 + 2500 * k) / SR) * (0.5 + 0.5 * np.sin(2 * np.pi * 18 * t)) * 0.15
    x = (nz + tone * 0.35 + shimmer) * (k ** 1.5) * env_adsr(n, 0.01, 0.01, 1, 0.05, 1)
    return finish(reverb(x, 1.0, 0.3))


def s_boom():
    d = 3.0
    n = int(d * SR)
    t = t_axis(d)
    sub = sweep_sine(90, 28, d, 0.4) * exp_decay(n, 0.9)
    body = svf(noise(d), 200 + 3000 * np.exp(-t / 0.15), q=0.8) * exp_decay(n, 0.6) * 1.4
    crack = svf(noise(d), 6000, mode="hp") * exp_decay(n, 0.05) * 0.8
    rumble = svf(noise(d), 90) * exp_decay(n, 1.4) * 2.5
    x = sub * 1.3 + body + crack + rumble
    return finish(reverb(x, 2.2, 0.35, 2.5), 0.3)


def s_impact():
    d = 0.45
    n = int(d * SR)
    thump = sweep_sine(160, 45, d, 0.3) * exp_decay(n, 0.08)
    click = svf(noise(d), 2500) * exp_decay(n, 0.015)
    return finish(reverb(thump * 1.4 + click, 0.4, 0.15, 0.6))


def s_whoosh():
    d = 0.7
    t = t_axis(d)
    n = len(t)
    k = t / d
    bell = np.sin(np.pi * k) ** 2
    x = svf(noise(d), 3500 - 2800 * k, q=2.5, mode="bp") * bell * 2
    return finish(reverb(x, 0.6, 0.2, 0.8))


def s_slash():
    d = 0.4
    t = t_axis(d)
    n = len(t)
    k = t / d
    swish = svf(noise(d), 7000 - 4000 * k, q=4, mode="bp") * np.sin(np.pi * np.clip(k * 1.6, 0, 1)) * 2
    ring = sum(np.sin(2 * np.pi * f * t) for f in [2100, 3170, 4420]) * exp_decay(n, 0.12) * 0.12
    return finish(reverb(swish + ring, 0.5, 0.2, 0.7))


def s_beam():
    d = 2.2
    t = t_axis(d)
    n = len(t)
    vib = 1 + 0.01 * np.sin(2 * np.pi * 6 * t)
    buzz = sum(saw(f * vib, n) / (i + 1) for i, f in enumerate([110, 220.7, 331]))
    buzz = svf(buzz, 1800 + 800 * np.sin(2 * np.pi * 3 * t), q=1.5)
    hiss = svf(noise(d), 5000, mode="hp") * 0.15
    x = (buzz * 0.6 + hiss) * env_adsr(n, 0.08, 0.2, 0.8, 0.5, 0.8)
    return finish(reverb(x, 1.0, 0.25))


def s_blackhole():
    d = 4.5
    t = t_axis(d)
    n = len(t)
    k = t / d
    drone = sum(np.sin(2 * np.pi * np.cumsum(np.full(n, f) * (1 - 0.3 * k)) / SR) for f in [41, 43.5, 55, 82])
    warble = 0.5 + 0.5 * np.sin(2 * np.pi * (2 + 6 * k) * t)
    suck = svf(noise(d), 200 + 1800 * k ** 3, q=4, mode="bp") * k ** 2 * 1.5
    x = (drone * 0.4 * warble + suck) * env_adsr(n, 1.2, 0.1, 1, 0.4, 1)
    return finish(reverb(x, 2.5, 0.4, 2.5), 0.3)


def s_choir():
    d = 3.2
    t = t_axis(d)
    n = len(t)
    out = np.zeros(n)
    chord = [261.6, 329.6, 392.0, 523.2, 659.3]
    for f in chord:
        for det in (-0.004, 0, 0.005):
            vib = 1 + 0.006 * np.sin(2 * np.pi * (5 + rng.uniform(-0.5, 0.5)) * t + rng.uniform(0, 6))
            v = saw(f * (1 + det) * vib, n)
            out += v
    # crude "aah" formants
    out = svf(out, 800, q=4, mode="bp") * 0.8 + svf(out, 1150, q=5, mode="bp") * 0.5 + svf(out, 2900, q=6, mode="bp") * 0.25
    out *= env_adsr(n, 0.6, 0.3, 0.9, 1.0, 0.9)
    return finish(reverb(out, 3.0, 0.5, 3.0), 0.4)


def s_demon():
    d = 2.6
    t = t_axis(d)
    n = len(t)
    k = t / d
    f = 70 - 30 * k
    growl = saw(f, n) + 0.5 * saw(f * 1.5, n) + 0.4 * saw(f * 0.5, n)
    am = 0.6 + 0.4 * np.sin(2 * np.pi * 23 * t + 3 * np.sin(2 * np.pi * 3 * t))
    rasp = svf(noise(d), 900, q=2, mode="bp") * 0.8
    x = np.tanh((growl * am + rasp) * 2.5)
    x = svf(x, 1600, q=0.9) * env_adsr(n, 0.05, 0.4, 0.8, 0.8, 0.8)
    return finish(reverb(x, 1.8, 0.3, 2.0), 0.3)


def s_ult():
    d = 3.0
    t = t_axis(d)
    n = len(t)
    stack = sum(saw(f * (1 + 0.002 * i), n) / (1 + i * 0.3) for i, f in enumerate([55, 55.3, 110, 110.6, 164.8, 220]))
    cutoff = 200 + 2200 * np.exp(-t / 0.5) + 500 * np.exp(-t / 2)
    x = np.tanh(svf(stack, cutoff, q=1.2) * 2.2)
    sub = np.sin(2 * np.pi * 41 * t) * 0.8
    x = (x + sub) * env_adsr(n, 0.02, 0.6, 0.75, 1.2, 0.75)
    return finish(reverb(x, 2.8, 0.4, 3.0), 0.4)


def s_teleport():
    d = 0.9
    t = t_axis(d)
    n = len(t)
    k = t / d
    mod = np.sin(2 * np.pi * np.cumsum(300 + 3000 * k) / SR) * (8 * (1 - k))
    zap = np.sin(2 * np.pi * np.cumsum(200 + 2400 * k ** 0.5) / SR + mod)
    sparkle = svf(noise(d), 8000, mode="hp") * (np.sin(2 * np.pi * 30 * t) > 0.6) * 0.3
    x = (zap * 0.7 + sparkle) * env_adsr(n, 0.005, 0.2, 0.6, 0.4, 0.6)
    return finish(reverb(x, 1.2, 0.35))


def bell(f, dur, tau):
    t = t_axis(dur)
    n = len(t)
    return np.sin(2 * np.pi * f * t + 2.5 * np.sin(2 * np.pi * f * 3.5 * t) * exp_decay(n, tau * 0.5)) * exp_decay(n, tau)


def s_select():
    d = 2.6
    n = int(d * SR)
    out = np.zeros(n)
    for i, f in enumerate([523.2, 659.3, 784.0, 1046.5, 1318.5]):
        start = int(i * 0.09 * SR)
        b = bell(f, d - i * 0.09, 0.7)
        out[start:start + len(b)] += b * 0.5
    sh = svf(noise(d), 9000, mode="hp") * exp_decay(n, 0.6) * 0.15
    return finish(reverb(out + sh, 2.0, 0.45), 0.3)


def s_holy():
    d = 2.0
    t = t_axis(d)
    n = len(t)
    out = sum(bell(f, d, 0.9) for f in [880, 1108.7, 1318.5, 1760]) * 0.4
    swell = np.sin(2 * np.pi * 440 * t) * env_adsr(n, 0.02, 0.5, 0.3, 1.0, 0.3) * 0.3
    sh = svf(noise(d), 10000, mode="hp") * exp_decay(n, 0.4) * 0.3
    return finish(reverb(out + swell + sh, 2.2, 0.45), 0.3)


def s_fire():
    d = 1.4
    t = t_axis(d)
    n = len(t)
    roar = svf(noise(d), 600 + 1500 * np.exp(-t / 0.25), q=0.8) * env_adsr(n, 0.02, 0.3, 0.6, 0.7, 0.6) * 1.6
    crackle = (rng.uniform(0, 1, n) > 0.9993) * rng.uniform(-1, 1, n)
    crackle = svf(crackle, 4000, mode="hp") * 6 * env_adsr(n, 0.01, 0.2, 0.6, 0.8, 0.6)
    return finish(reverb(roar + crackle, 1.0, 0.25))


def s_thunder():
    d = 2.8
    t = t_axis(d)
    n = len(t)
    crack = svf(noise(d), 3000, mode="hp") * exp_decay(n, 0.04) * 1.5
    zz = (rng.uniform(0, 1, n) > 0.995) * exp_decay(n, 0.15) * 2
    rumble = svf(noise(d), 120 + 200 * np.exp(-t / 0.3)) * exp_decay(n, 0.9) * 3 * (0.7 + 0.3 * np.sin(2 * np.pi * 4 * t))
    return finish(reverb(crack + zz + rumble, 2.5, 0.35, 2.5), 0.4)


def s_timestop():
    d = 2.4
    t = t_axis(d)
    n = len(t)
    k = t / d
    rev = svf(noise(d), 500 + 6000 * k ** 2, q=2, mode="bp") * k ** 3 * 1.5
    rev[int(0.9 * SR):] *= 0  # reversed swell that cuts off
    tone = sweep_sine(880, 110, d, 0.35) * np.where(t > 0.9, exp_decay(n, 0.8) * 1.0, 0)
    tick = np.zeros(n)
    for i in range(6):
        s = int((1.0 + i * 0.22) * SR)
        if s < n - 2000:
            tick[s:s + 2000] += bell(2400, 2000 / SR, 0.01)[:2000] * 0.5
    return finish(reverb(rev + tone * 0.6 + tick, 1.5, 0.4), 0.3)


# ---------------------------------------------------------------- Entity 666

def s_scream():
    d = 1.4
    t = t_axis(d)
    n = len(t)
    k = t / d
    f0 = 520 + 900 * k ** 0.4 + 60 * np.sin(2 * np.pi * 9 * t)
    voice = sum(saw(f0 * r, n) / (i + 1) for i, r in enumerate([1, 1.012, 0.497, 1.51]))
    rasp = noise(d) * 0.9
    x = voice * 0.6 + rasp
    x = sum(svf(x, fc, q=6, mode="bp") * g for fc, g in ((900, 1.0), (2400, 0.8), (3600, 0.6)))
    x = np.tanh(x * 4) * env_adsr(n, 0.01, 0.15, 0.9, 0.5, 0.85)
    return finish(reverb(x, 1.6, 0.35, 2.0), 0.2)


def s_whisper():
    d = 3.2
    t = t_axis(d)
    n = len(t)
    breath = noise(d)
    out = np.zeros(n)
    pos = 0.05
    while pos < d - 0.3:
        L = rng.uniform(0.08, 0.28)
        a, b = int(pos * SR), int(min(d, pos + L) * SR)
        seg = breath[a:b]
        f1, f2 = rng.uniform(500, 1100), rng.uniform(1500, 3200)
        s = svf(seg, f1, q=5, mode="bp") + 0.7 * svf(seg, f2, q=6, mode="bp") + 0.3 * svf(seg, 6500, mode="hp")
        s *= np.sin(np.pi * np.linspace(0, 1, len(s))) ** 1.5
        out[a:b] += s * rng.uniform(0.5, 1.0)
        pos += L + rng.uniform(0.0, 0.12)
    out *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * t)
    return finish(reverb(out, 2.5, 0.55, 2.5), 0.4)


def s_crunch():
    d = 1.0
    n = int(d * SR)
    t = t_axis(d)
    x = np.zeros(n)
    for c in range(14):
        at = int(rng.uniform(0.0, 0.55) * SR)
        L = int(rng.uniform(0.01, 0.04) * SR)
        burst = svf(rng.uniform(-1, 1, L), rng.uniform(1500, 5000), q=1.5, mode="bp") * np.exp(-np.arange(L) / (0.006 * SR))
        x[at:at + L] += burst * rng.uniform(0.6, 1.4)
    squelch = svf(noise(d), 300 + 900 * np.abs(np.sin(2 * np.pi * 3 * t)), q=4, mode="bp") * exp_decay(n, 0.35) * 1.5
    thump = sweep_sine(110, 40, d, 0.3) * exp_decay(n, 0.1)
    return finish(reverb(x * 1.2 + squelch + thump, 0.5, 0.2, 0.8), 0.2)


def s_heartbeat():
    d = 1.1
    n = int(d * SR)
    x = np.zeros(n)
    for at, amp in ((0.0, 1.0), (0.24, 0.75)):
        a = int(at * SR)
        L = int(0.25 * SR)
        beat = sweep_sine(70, 38, 0.25, 0.5) * np.exp(-np.arange(L) / (0.05 * SR))
        x[a:a + L] += beat * amp
    x = svf(x, 180) * 2
    return finish(x, 0.05)


def s_static():
    d = 0.7
    n = int(d * SR)
    t = t_axis(d)
    x = noise(d)
    gate = (np.sin(2 * np.pi * (7 + 30 * t) * t + 3 * np.sin(2 * np.pi * 5 * t)) > 0.1).astype(float)
    crushed = np.round(x * 3) / 3
    buzz = np.sign(np.sin(2 * np.pi * 60 * t)) * 0.4
    y = (crushed * 0.8 + buzz) * gate * env_adsr(n, 0.005, 0.1, 0.8, 0.15, 0.8)
    return finish(svf(y, 7000), 0.05)


def s_drone():
    d = 6.0
    t = t_axis(d)
    n = len(t)
    low = sum(saw(f, n) / (i + 1) for i, f in enumerate([36.7, 37.1, 51.9, 73.4, 77.8]))
    low = svf(low, 220 + 120 * np.sin(2 * np.pi * 0.15 * t), q=1.5)
    hiss = svf(noise(d), 2500 + 1500 * np.sin(2 * np.pi * 0.21 * t), q=8, mode="bp") * 0.15
    groan = np.sin(2 * np.pi * np.cumsum(98 + 6 * np.sin(2 * np.pi * 0.4 * t)) / SR) * 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.11 * t))
    x = (np.tanh(low * 1.5) + hiss + groan) * env_adsr(n, 1.2, 0.5, 0.9, 1.5, 0.9)
    return finish(reverb(x, 3.5, 0.5, 3.0), 0.8)


SOUNDS = {
    "charge": s_charge, "boom": s_boom, "impact": s_impact, "whoosh": s_whoosh,
    "slash": s_slash, "beam": s_beam, "blackhole": s_blackhole, "choir": s_choir,
    "demon": s_demon, "ult": s_ult, "teleport": s_teleport, "select": s_select,
    "holy": s_holy, "fire": s_fire, "thunder": s_thunder, "timestop": s_timestop,
    "scream": s_scream, "whisper": s_whisper, "crunch": s_crunch, "heartbeat": s_heartbeat,
    "static": s_static, "drone": s_drone,
}


def write_ogg(name, data, out_dir):
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as td:
        wav_path = os.path.join(td, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        out = os.path.join(out_dir, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", out], check=True)
    return out


if __name__ == "__main__":
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    only = set(sys.argv[2:])
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        p = write_ogg(name, fn(), out_dir)
        print(f"{name}: {os.path.getsize(p) // 1024} KB")
