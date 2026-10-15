import numpy as np, soundfile as sf
wav, sr = sf.read("assets/bgm/seed.wav", dtype="float32")
if wav.ndim > 1: wav = wav.mean(axis=1)
target = 105.0
need = int(round(target * sr))
# crossfade-loop the 28s seed up to the target, avoiding a seam click
xf = int(1.5 * sr)
out = wav.copy()
while len(out) < need + xf:
    tail = out[-xf:]
    head = wav[:xf]
    fade = np.linspace(1.0, 0.0, xf, dtype=np.float32)
    blended = tail * fade + head * (1.0 - fade)
    out = np.concatenate([out[:-xf], blended, wav[xf:]])
out = out[:need]
# gentle fade in / out
fi, fo = int(1.2 * sr), int(3.0 * sr)
out[:fi] *= np.linspace(0, 1, fi, dtype=np.float32)
out[-fo:] *= np.linspace(1, 0, fo, dtype=np.float32)
out = np.clip(out * 0.85, -1, 1)
sf.write("assets/bgm/track.wav", out, sr)
print("wrote assets/bgm/track.wav", round(len(out)/sr, 2), "s")
