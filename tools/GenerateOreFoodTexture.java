import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 生成 {@code orefood:item/ore_food} 的 16×16 物品贴图（PNG）。
 *
 * <p>为什么要「生成」而不是直接放一张图：本仓库不携带二进制美术资源，
 * 而 NeoForm 的 assets 索引里只有声音与图标、没有物品贴图，本机也没有完整的
 * {@code .minecraft/assets}。用代码画出来可以让贴图<b>可复现、可审阅</b>，
 * 想换风格改这里的数值即可。
 *
 * <p>用法（Java 21 单文件源码启动）：
 * <pre>java -Djava.awt.headless=true tools/GenerateOreFoodTexture.java &lt;输出.png&gt; [色调]</pre>
 *
 * <p>色调取值：{@code iron} / {@code gold} / {@code diamond} / {@code netherite}，
 * 省略时按 iron 生成。
 */
public final class GenerateOreFoodTexture {

    private static final int SIZE = 16;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: GenerateOreFoodTexture <output.png> [iron|gold|diamond|netherite]");
            System.exit(1);
        }
        String tint = args.length > 1 ? args[1] : "iron";

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] palette = paletteFor(tint);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // 椭圆形的「肉块」：中心略偏上，横向比纵向宽。
                double dx = (x - 7.5) / 6.3;
                double dy = (y - 7.0) / 4.1;
                double distance = dx * dx + dy * dy;

                if (distance > 1.0) {
                    continue; // 透明
                }

                int argb;
                if (distance > 0.70) {
                    argb = palette[0]; // 描边
                } else {
                    // 左上高光 → 右下阴影，制造金属的斜面感。
                    double light = ((x - 1.5) + (y - 2.5)) / 21.0;
                    light = Math.max(0.0, Math.min(1.0, light));
                    int base = lerpColor(palette[1], palette[2], light);
                    int noise = (hash(x, y) % 15) - 7; // 轻微噪点，避免死板的纯色
                    argb = shade(base, noise);
                }
                image.setRGB(x, y, argb);
            }
        }

        File output = new File(args[0]);
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(image, "png", output);
        System.out.println("wrote " + output.getPath() + " (" + tint + ")");
    }

    /** @return {描边, 亮部, 暗部} 三个 ARGB 颜色。 */
    private static int[] paletteFor(String tint) {
        return switch (tint) {
            case "gold" -> new int[] {0xFF4A3708, 0xFFF7E27A, 0xFFB08A18};
            case "diamond" -> new int[] {0xFF0E4A4A, 0xFF9BF2E4, 0xFF31A79A};
            case "netherite" -> new int[] {0xFF1A1418, 0xFF6E6270, 0xFF332B33};
            default -> new int[] {0xFF2B2B2E, 0xFFE2E2E6, 0xFF7C7C84}; // iron
        };
    }

    private static int lerpColor(int from, int to, double t) {
        int a = (int) Math.round(((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) Math.round(((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 整体加减亮度，各通道等量偏移并夹到 0..255。 */
    private static int shade(int argb, int delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = clamp(((argb >>> 16) & 0xFF) + delta);
        int g = clamp(((argb >>> 8) & 0xFF) + delta);
        int b = clamp((argb & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** 位置相关的确定性伪随机，保证每次生成的贴图完全一致。 */
    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return Math.abs(h ^ (h >>> 16));
    }

    private GenerateOreFoodTexture() {
        throw new AssertionError("No instances.");
    }
}
