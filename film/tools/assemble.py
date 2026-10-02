"""
Cuts the rendered shots together, grades them, and lays the score under the picture.

    python film/tools/assemble.py final            # film/build/shots/final/*.mp4 -> film/build/Kraft-final.mp4
    python film/tools/assemble.py final --tag v2   # -> film/build/releases/v2/Kraft-v2-{master,web}.mp4

The grade: a soft highlight bloom (glows and chrome read as light), a gentle S-curve, a vignette,
and fine animated grain, which also keeps the near-black gradients from banding in 8-bit.
"""
import json
import os
import re
import subprocess
import sys

ORDER = ["open", "title", "orbs", "beam", "metal", "gooey", "voice", "bots", "image", "code", "platforms", "finale", "end"]
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def run(args, capture=False):
    print("  $", " ".join(a if " " not in a else repr(a) for a in args[:12]), "…" if len(args) > 12 else "")
    return subprocess.run(args, check=True, capture_output=capture, text=True)


def main(quality, picture=True, tag=None):
    shots = os.path.join(ROOT, "build/shots", quality)
    out_dir = os.path.join(ROOT, "build")
    listing = os.path.join(shots, "concat.txt")
    with open(listing, "w") as f:
        for name in ORDER:
            f.write(f"file '{os.path.join(shots, name + '.mp4')}'\n")

    # 1. Picture: concat (identical streams) then grade.
    grade = (
        "[0:v]format=gbrpf32le,split=2[base][hi];"
        "[hi]curves=all='0/0 0.55/0 0.8/0.35 1/1',gblur=sigma=18:steps=3[bloom];"
        "[base][bloom]blend=all_mode=screen:all_opacity=0.35,"
        "curves=all='0/0.012 0.25/0.235 0.5/0.52 0.75/0.79 1/1',"
        "format=yuv444p,"
        "vignette=angle=PI/4.6:mode=forward,"
        "noise=c0s=5:c0f=t+u:c1s=2:c1f=t+u:c2s=2:c2f=t+u,"
        "format=yuv420p[v]"
    )
    graded = os.path.join(out_dir, f"picture-{quality}.mp4")
    if picture:
        run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", listing,
             "-filter_complex", grade, "-map", "[v]",
             "-c:v", "libx264", "-preset", "slow", "-crf", "14", "-tune", "grain", "-profile:v", "high",
             "-x264-params", "aq-mode=3", graded])

    # 2. Sound: two-pass loudness normalisation to -14 LUFS, -1.5 dBTP, linear (keeps the arc).
    score = os.path.join(ROOT, "build/audio/score.wav")
    probe = run(["ffmpeg", "-hide_banner", "-i", score, "-af", "loudnorm=I=-14:TP=-1.5:LRA=11:print_format=json", "-f", "null", "-"], capture=True)
    stats = json.loads(re.findall(r"\{[^{}]*\}", probe.stderr)[-1])
    ln = ("loudnorm=I=-14:TP=-1.5:LRA=11:linear=true:"
          f"measured_I={stats['input_i']}:measured_TP={stats['input_tp']}:measured_LRA={stats['input_lra']}:"
          f"measured_thresh={stats['input_thresh']}:offset={stats['target_offset']}")

    release = os.path.join(out_dir, "releases", tag) if tag else out_dir
    os.makedirs(release, exist_ok=True)
    final = os.path.join(release, f"Kraft-{tag}-master.mp4" if tag else f"Kraft-{quality}.mp4")
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", graded, "-i", score,
         "-map", "0:v", "-map", "1:a", "-c:v", "copy",
         "-af", ln + ",aresample=48000", "-c:a", "aac", "-b:a", "320k",
         "-shortest", "-movflags", "+faststart", final])
    print("wrote", final)

    # 3. A web copy: same picture, grain-preserving encode capped at 40 Mbps for sharing and upload.
    web = os.path.join(release, f"Kraft-{tag}-web.mp4" if tag else f"Kraft-{quality}-web.mp4")
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", final, "-map", "0",
         "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-tune", "grain", "-maxrate", "40M", "-bufsize", "80M",
         "-profile:v", "high", "-pix_fmt", "yuv420p", "-c:a", "copy", "-movflags", "+faststart", web])
    print("wrote", web)


if __name__ == "__main__":
    tag = sys.argv[sys.argv.index("--tag") + 1] if "--tag" in sys.argv else None
    main(sys.argv[1] if len(sys.argv) > 1 else "preview", picture="--sound-only" not in sys.argv, tag=tag)
