import json, os, re
from faster_whisper import WhisperModel

# The spoken text is KNOWN exactly (SCRIPT.md). ASR only supplies TIMING.
# Whisper mis-recognises Chinese homophones, so we keep the authored characters
# and take timings from ASR, distributing word starts across real speech spans.
model = WhisperModel("small", device="cpu", compute_type="int8",
                     download_root=os.path.join(os.environ["USERPROFILE"], ".cache", "hf-whisper"))

def chunk(chars, n):
    """Split authored characters into n roughly equal contiguous pieces."""
    out, base, rem = [], 0, len(chars)
    for i in range(n):
        take = rem // (n - i)
        out.append(chars[base:base + take])
        base += take
        rem -= take
    return out

lines = {
 "01": "把矿石做成牛排？吃一次扣一点耐久，一块能啃一百二十八口。",
 "02": "你见过用铜、铁、金、钻石做的牛排吗？整个模组只有一个物品编号，却长出了五种矿物牛排。",
 "04": "它们自带五个食物附魔，只能附在食物上，吃下去的瞬间触发药水效果。来一个一个看。",
 "05": "强效恢复给你瞬间治疗和生命恢复；肾上腺素给你力量和速度；强身健体给你生命吸收，还有固定的抗性提升二级，稳稳减伤百分之四十。",
 "07": "合成是逐级升上去的：八块矿物围一圈，中间放上一级牛排，从熟牛排一路升到钻石牛排。最后一步换成锻造台，成型模板加上下界合金锭，配一块钻石牛排，才是下界合金牛排。",
 "09": "所有数值都写在数据包里，改一行，重进世界就生效，你甚至可以自己加一块红石牛排；装了界面模组，五种牛排会分开显示，悬浮提示框按原版药水格式列出效果和时长。支持三个平台，开源协议，自由修改分发。喜欢的话，点个赞收个藏，评论区聊聊你想加的矿物。",
}

out = {}
for fid, text in lines.items():
    segs, info = model.transcribe(f"assets/voice/{fid}.wav", language="zh",
                                  word_timestamps=True, vad_filter=True)
    segs = list(segs)
    n_asr = sum(len(s.words or []) for s in segs)
    # spoken character count (drop punctuation, which ASR never emits)
    spoken = [c for c in text if re.match(r"[\u4e00-\u9fff]", c)]
    if n_asr == 0:
        print(fid, "NO ASR WORDS"); out[fid] = []; continue
    pieces = chunk(spoken, n_asr)
    # flatten ASR timings
    times = []
    for s in segs:
        for w in (s.words or []):
            times.append((w.start, w.end))
    words = []
    for (st, en), piece in zip(times, pieces):
        if piece:
            words.append({"text": piece, "start": round(st, 3), "end": round(en, 3)})
    # snap any leftover characters (from rounding) onto the final word
    used = sum(len(w["text"]) for w in words)
    if used < len(spoken) and words:
        words[-1]["text"] += "".join(spoken[used:])
    out[fid] = words
    print(fid, f"asr={n_asr}", f"chars={len(spoken)}", f"words={len(words)}", flush=True)

json.dump(out, open("tmp_words.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("done")
