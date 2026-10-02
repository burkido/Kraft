# film

The Kraft showcase film, rendered from the real effects, frame by frame.

Each shot is a composable in `src/main/kotlin/.../shots`, laid out on a 960 × 540 dp stage.
`Film.kt` renders each shot in an offscreen `ImageComposeScene` on a virtual clock:
- The frame clock and coroutine delays both advance by exactly one frame per render.
- Pointer events come from a scripted "hand" (`Shot.touch`).
- Frames are piped straight into ffmpeg.

Every run produces identical output. The output resolution only changes the density.

## Render

```sh
film/tools/render.sh final 4                              # all shots, 1080p60 with motion blur, 4 JVMs
./gradlew :film:render -Pshots=metal -Pquality=preview    # one shot, 540p30
python film/tools/score.py                                # the soundtrack -> film/build/audio/score.wav
python film/tools/assemble.py final                       # cut, grade, mux -> film/build/Kraft-final.mp4
```

Both the renderer and the score take a voice: `--voice onyx` (the default, male) or `--voice sage` (female, v1 and v2).
With Gradle, pass it as `-Pvoice=sage`.

The available qualities are `draft` (360p30), `preview` (540p30), `final` (1080p60, two sub-frames per
frame) and `uhd` (2160p60). The Python tools need `numpy scipy soundfile` and `ffmpeg` on the path.

## Timing

The film is 96 BPM: one bar is 2.5 s, and each chapter is 5 s (two bars). Cuts land on downbeats.
The edit points in `tools/score.py` must match the shot order and lengths in `Shots.kt`.

## Assets

The following were generated with OpenAI for this film:
- `assets/gen_*.jpg`, the images revealed in the image-generation chapter, made with `gpt-image-1`.
- `assets/voice_<take>.wav`, the voice line, made with `gpt-4o-mini-tts`: `onyx` (male) and `sage` (female).
- `assets/voice_<take>_level.txt`, the take's loudness envelope at 120 Hz, which drives the voice glow.
  Each take's word onsets are in `VoiceTakes` in `Chapters2.kt`.

The score and sound effects are synthesized by `tools/score.py`. They use no samples.

## Releases

Each release is kept in `film/build/releases/<tag>/`, as a master and a web copy.

- **v1**: the first cut. Its rendered shots and score are kept in `releases/v1/shots` and `releases/v1/score.wav`.
- **v2**: the finale's image-generation card now reveals a chrome sculpture (`assets/gen_chrome.jpg`) instead of the library's demo photos. Everything else is v1.

v2 changes only one card, so it was not a fresh re-render: some cards in the finale use random values. Instead:

1. Render the new finale (`finale-v2-raw.mp4`) and a matte of that card alone (`--shots finale-matte`).
2. Merge the new card into the v1 finale through the matte:

```sh
cd film/build
ffmpeg -i releases/v1/shots/finale.mp4 -i shots/final/finale-v2-raw.mp4 -i shots/final/finale-matte.mp4 \
  -filter_complex "[0:v]format=gbrp[a];[1:v]format=gbrp[b];[2:v]format=gray,format=gbrp[m];[a][b][m]maskedmerge,format=yuv444p[v]" \
  -map "[v]" -c:v libx264 -crf 10 -pix_fmt yuv444p shots/final/finale.mp4
cd ../.. && python film/tools/assemble.py final --tag v2
```

- **v3**: v2 with a male voice (`onyx`). Only the voice chapter was re-rendered; it has no random values.
  Its frames before the voice starts match v1 exactly. Every other shot is v2's. The score is kept in `releases/v3/score.wav`.

```sh
./gradlew :film:render -Pshots=voice -Pquality=final -Pvoice=onyx
python film/tools/score.py --voice onyx
python film/tools/assemble.py final --tag v3
```
