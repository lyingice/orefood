#!/usr/bin/env python3
"""把五种矿物牛排的贴图合成循环动图（APNG + GIF）。

用法：
    python tools/make_beef_animation.py                       # 源 = 模组贴图目录
    python tools/make_beef_animation.py --source docs/images  # 源 = 指定目录
    python tools/make_beef_animation.py --size 400 --duration 500

源目录里需要有 copper_beef.png / iron_beef.png / golden_beef.png /
diamond_beef.png / netherite_beef.png 五张正方形贴图，大小不限（一律 NEAREST 缩放）。

产物（写到 docs/images/）：
    ore_beefs_animation.png  APNG
    ore_beefs_animation.gif  GIF（GIF 只有 1 位透明，像素画正好只有「全透明/
                             全不透明」，因此无损）
    ore_beefs_frames.png     五帧横向拼图（静态查看 / 粘贴用）

顺序固定为 铜 → 铁 → 金 → 钻石 → 下界合金。
放大一律用 NEAREST：像素画插值会糊边，这是本项目贴图的硬要求。
"""

import argparse
from pathlib import Path

from PIL import Image, ImageSequence

REPO = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = REPO / "common/src/main/resources/assets/orefood/textures/item"
OUT = REPO / "docs/images"

# 升级链顺序，别改：动图的叙事就是「一级一级升上去」。
TIERS = ["copper", "iron", "golden", "diamond", "netherite"]
SUFFIX = "_beef"

ALPHA_THRESHOLD = 128    # GIF 二值透明的分界
TRANSPARENT_INDEX = 255  # GIF 调色板里留给透明的索引

NEAREST = getattr(getattr(Image, "Resampling", Image), "NEAREST")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="合成矿物牛排动图")
    parser.add_argument("--source", default=None,
                       help="贴图所在目录，默认使用模组贴图目录")
    parser.add_argument("--size", type=int, default=256, help="缩放后的边长，默认 256")
    parser.add_argument("--duration", type=int, default=300, help="每帧毫秒，默认 300")
    return parser.parse_args()


def load_frames(source: Path, size: int) -> list[Image.Image]:
    """按升级链顺序读入五张贴图，逐个放大到 size，返回 RGBA 帧列表。"""
    frames = []
    for tier in TIERS:
        name = tier + SUFFIX
        path = source / f"{name}.png"
        if not path.is_file():
            raise SystemExit(f"找不到贴图：{path}")
        with Image.open(path) as src:
            if src.width != src.height:
                raise SystemExit(f"{path.name} 不是正方形（实际 {src.size}）")
            original = src.size
            # 先缩放到目标边长，再参与合成——顺序不能反。
            frames.append(src.convert("RGBA").resize((size, size), NEAREST))
        rat = size / original[0]
        print(f"  {name}: {original[0]}x{original[1]} -> {size}x{size}（{rat:g}x）")
    return frames


def save_apng(frames: list[Image.Image], path: Path, duration: int) -> None:
    frames[0].save(
        path,
        save_all=True,
        append_images=frames[1:],
        duration=duration,
        loop=0,
        disposal=2,          # 每帧前清空画布，避免残影
        format="PNG",
    )


def to_gif_frame(frame: Image.Image) -> Image.Image:
    """RGBA → P 模式：实体像素进调色板，透明像素统一指到 TRANSPARENT_INDEX。

    GIF 的透明只有一位，所以 alpha 必须二值化；本项目贴图是像素画，
    每个像素非「完全透明」即「完全不透明」，这一步没有实际损失。
    """
    alpha = frame.getchannel("A")
    quantized = frame.convert("RGB").quantize(colors=TRANSPARENT_INDEX,
                                              method=Image.Quantize.MEDIANCUT)
    palette = (quantized.getpalette() or [])[:]
    palette += [0] * (768 - len(palette))
    quantized.putpalette(palette)
    transparent_mask = alpha.point(lambda a: 255 if a < ALPHA_THRESHOLD else 0)
    quantized.paste(TRANSPARENT_INDEX, transparent_mask)
    return quantized


def save_gif(frames: list[Image.Image], path: Path, duration: int) -> None:
    prepared = [to_gif_frame(frame) for frame in frames]
    prepared[0].save(
        path,
        save_all=True,
        append_images=prepared[1:],
        duration=duration,
        loop=0,
        disposal=2,
        transparency=TRANSPARENT_INDEX,
        optimize=False,      # 关掉局部帧优化：透明 + 局部帧很容易出残影
    )


def visible_count(frame: Image.Image) -> int:
    return sum(1 for value in frame.getchannel("A").tobytes() if value >= ALPHA_THRESHOLD)


def verify(frames: list[Image.Image], path: Path, label: str, size: int) -> None:
    """读回来核对帧数、尺寸、每帧可见像素数，并抽样比对颜色。"""
    with Image.open(path) as check:
        if not getattr(check, "is_animated", False) or check.n_frames != len(frames):
            raise SystemExit(f"{label} 校验失败：is_animated={getattr(check, 'is_animated', None)} "
                             f"n_frames={check.n_frames}")
        if check.size != (size, size):
            raise SystemExit(f"{label} 校验失败：尺寸 {check.size}，应为 {(size, size)}")
        for index, frame in enumerate(ImageSequence.Iterator(check)):
            rgba = frame.convert("RGBA")
            if visible_count(rgba) != visible_count(frames[index]):
                raise SystemExit(f"{label} 第 {index} 帧可见像素数不符")
            for y in range(8, size, 32):
                for x in range(8, size, 32):
                    want = frames[index].getpixel((x, y))
                    if want[3] < ALPHA_THRESHOLD:
                        continue
                    got = rgba.getpixel((x, y))
                    if got[:3] != want[:3]:
                        raise SystemExit(f"{label} 第 {index} 帧 ({x},{y}) 颜色不符：{got[:3]} != {want[:3]}")
        print(f"  {label} 校验通过：{check.n_frames} 帧，{check.size[0]}x{check.size[1]}，"
              f"每帧 {check.info.get('duration')}ms，loop={check.info.get('loop')}")


def main() -> None:
    args = parse_args()
    source = Path(args.source).resolve() if args.source else DEFAULT_SOURCE
    size = args.size
    print(f"源目录：{source}")

    frames = load_frames(source, size)
    OUT.mkdir(parents=True, exist_ok=True)

    apng = OUT / "ore_beefs_animation.png"
    save_apng(frames, apng, args.duration)
    print(f"写出 {apng.relative_to(REPO)}")

    gif = OUT / "ore_beefs_animation.gif"
    save_gif(frames, gif, args.duration)
    print(f"写出 {gif.relative_to(REPO)}")

    strip_path = OUT / "ore_beefs_frames.png"
    strip = Image.new("RGBA", (size * len(frames), size), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        strip.paste(frame, (index * size, 0))
    strip.save(strip_path)
    print(f"写出 {strip_path.relative_to(REPO)}（{size * len(frames)}x{size} 横向拼图）")

    # 存进去容易、存错了看不出来，所以两个动图都读回来核对一遍。
    verify(frames, apng, "APNG", size)
    verify(frames, gif, "GIF", size)


if __name__ == "__main__":
    main()
