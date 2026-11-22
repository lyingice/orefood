package com.lyingice.orefood.tools;

import com.lyingice.orefood.OreFoodTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成 2.0 矿物食品所需的资源文件（一次性生成、结果提交仓库，**不进构建流程**）。
 *
 * <p>生成内容：
 * <ul>
 *   <li>{@code common/.../models/item/<name>.json}（1.20.1 / 1.21.1 共用）</li>
 *   <li>{@code neoforge-26.1.2/.../assets/orefood/items/<name>.json}</li>
 *   <li>三平台各自的配方（注意三边的 JSON 形状不同，见下面三个方法）</li>
 *   <li>四类食物/耐久标签（三个平台各两份）</li>
 *   <li>{@code common/.../lang/zh_cn.json} 与 {@code en_us.json}（合并，保留既有键）</li>
 * </ul>
 *
 * <p><b>清单只有一份</b>：矿物与食物列表直接来自 {@link OreFoodTable}——
 * 本类只额外维护「中英文名」与「合成用的原版物品」，那是资源侧信息，Java 表里不需要。
 *
 * <p><b>用法</b>（仓库根目录执行）：
 * <pre>{@code
 * javac -encoding UTF-8 -d tools/out \
 *     common/src/main/java/com/lyingice/orefood/OreFoodTable.java \
 *     tools/src/com/lyingice/orefood/tools/GenerateOreFoodAssets.java
 * java -cp tools/out com.lyingice.orefood.tools.GenerateOreFoodAssets .
 * }</pre>
 *
 * <p>默认**不覆盖已存在的模型与配方**（保护 1.x 时代手写的那 5 份的格式与 diff），
 * 加 {@code --force} 才覆盖。标签与语言文件总是重写，因为「列全 50 项」正是目的。
 */
public final class GenerateOreFoodAssets {

    /** 矿物等级的资源侧信息。 */
    private record MineralInfo(String cn, String en, String material) {
    }

    /**
     * 食物基底的资源侧信息。
     *
     * <p>合成用的原版物品不在这里——那是 {@link OreFoodTable.FoodBase#baseItemId()}，
     * 免得两处清单走样。
     */
    private record FoodInfo(String cn, String en) {
    }

    /** 必须覆盖 {@link OreFoodTable.MineralTier} 的每一个值。 */
    private static final Map<String, MineralInfo> MINERALS = Map.of(
            "copper", new MineralInfo("铜", "Copper", "minecraft:copper_ingot"),
            "iron", new MineralInfo("铁", "Iron", "minecraft:iron_ingot"),
            "golden", new MineralInfo("金", "Golden", "minecraft:gold_ingot"),
            "diamond", new MineralInfo("钻石", "Diamond", "minecraft:diamond"),
            "netherite", new MineralInfo("下界合金", "Netherite", "minecraft:netherite_ingot"));

    /** 必须覆盖 {@link OreFoodTable.FoodBase} 的每一个值。 */
    private static final Map<String, FoodInfo> FOODS = Map.ofEntries(
            Map.entry("beef", new FoodInfo("牛排", "Beef")),
            Map.entry("bread", new FoodInfo("面包", "Bread")),
            Map.entry("chicken", new FoodInfo("鸡肉", "Chicken")),
            Map.entry("cod", new FoodInfo("鳕鱼", "Cod")),
            Map.entry("mutton", new FoodInfo("羊肉", "Mutton")),
            Map.entry("porkchop", new FoodInfo("猪排", "Porkchop")),
            Map.entry("pufferfish", new FoodInfo("河豚", "Pufferfish")),
            Map.entry("rabbit", new FoodInfo("兔肉", "Rabbit")),
            Map.entry("rotten_flesh", new FoodInfo("腐肉", "Rotten Flesh")),
            Map.entry("salmon", new FoodInfo("鲑鱼", "Salmon")));

    private static final String MOD_ID = "orefood";

    /** 默认角色用铁锭：与 1.x 的第一个非基础等级一致。 */
    private static final String TEMPLATE_ITEM = "minecraft:netherite_upgrade_smithing_template";

    private static int written;
    private static int skipped;

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        boolean force = args.length > 1 && args[1].equals("--force");

        if (!Files.isDirectory(root.resolve("common"))) {
            throw new IllegalArgumentException("请把仓库根目录作为第一个参数传进来：" + root);
        }
        verifyNames();

        List<OreFoodTable.ItemKey> keys = OreFoodTable.all();
        System.out.println("生成 " + keys.size() + " 个物品的资源（force=" + force + "）");

        List<String> ids = new ArrayList<>(keys.size());
        Map<String, String> zhNames = new LinkedHashMap<>();
        Map<String, String> enNames = new LinkedHashMap<>();

        for (OreFoodTable.ItemKey key : keys) {
            String tier = key.tier().key();
            String food = key.base().key();
            String id = key.id();
            MineralInfo mineral = MINERALS.get(tier);
            FoodInfo foodInfo = FOODS.get(food);

            ids.add(MOD_ID + ":" + id);
            zhNames.put("item." + MOD_ID + "." + id, mineral.cn() + foodInfo.cn());
            enNames.put("item." + MOD_ID + "." + id, mineral.en() + " " + foodInfo.en());

            // 模型：1.20.1 / 1.21.1 共用 common 的 models/item；26.1.2 另有一套 items/
            writeIfAbsent(root.resolve("common/src/main/resources/assets/" + MOD_ID + "/models/item/" + id + ".json"),
                    itemModel(id), force);
            writeIfAbsent(root.resolve("neoforge-26.1.2/src/main/resources/assets/" + MOD_ID + "/items/" + id + ".json"),
                    itemModel2612(id), force);

            // 配方：三平台各一份，形状不同
            writeIfAbsent(root.resolve("forge-1.20.1/src/main/resources/data/" + MOD_ID + "/recipes/" + id + ".json"),
                    recipe1201(key, mineral, foodInfo), force);
            writeIfAbsent(root.resolve("neoforge-1.21.1/src/main/resources/data/" + MOD_ID + "/recipe/" + id + ".json"),
                    recipe1211(key, mineral, foodInfo), force);
            writeIfAbsent(root.resolve("neoforge-26.1.2/src/main/resources/data/" + MOD_ID + "/recipe/" + id + ".json"),
                    recipe2612(key, mineral, foodInfo), force);
        }

        // 标签：必须列全 50 项，所以总是重写
        writeAlways(root.resolve("forge-1.20.1/src/main/resources/data/forge/tags/items/food.json"), tag(ids));
        writeAlways(root.resolve("forge-1.20.1/src/main/resources/data/" + MOD_ID + "/tags/items/foods.json"), tag(ids));
        for (String module : List.of("neoforge-1.21.1", "neoforge-26.1.2")) {
            writeAlways(root.resolve(module + "/src/main/resources/data/c/tags/item/foods.json"), tag(ids));
            writeAlways(root.resolve(module
                    + "/src/main/resources/data/minecraft/tags/item/enchantable/durability.json"), tag(ids));
        }

        // 语言文件：合并（保留既有键与顺序，新增的追加在后面）
        for (String locale : List.of("zh_cn", "en_us")) {
            Path path = root.resolve("common/src/main/resources/assets/" + MOD_ID + "/lang/" + locale + ".json");
            mergeLang(path, locale.equals("zh_cn") ? zhNames : enNames, locale);
        }

        System.out.println("完成：写入/更新 " + written + " 个文件，跳过已存在 " + skipped + " 个");
    }

    /** 缺名字就报错——清单加东西时逼着人回来补。 */
    private static void verifyNames() {
        for (OreFoodTable.MineralTier tier : OreFoodTable.MineralTier.values()) {
            if (!MINERALS.containsKey(tier.key())) {
                throw new IllegalStateException("生成器缺少矿物 " + tier.key() + " 的中英文名与材料");
            }
        }
        for (OreFoodTable.FoodBase base : OreFoodTable.FoodBase.values()) {
            if (!FOODS.containsKey(base.key())) {
                throw new IllegalStateException("生成器缺少食物 " + base.key() + " 的中英文名与基底物品");
            }
        }
    }

    // ------------------------------------------------------------------
    // 各文件的 JSON 形状（照着 1.x 手写文件的样子来，避免无谓的格式 diff）
    // ------------------------------------------------------------------

    private static String itemModel(String id) {
        return "{\n"
                + "  \"parent\": \"minecraft:item/generated\",\n"
                + "  \"textures\": {\n"
                + "    \"layer0\": \"" + MOD_ID + ":item/" + id + "\"\n"
                + "  }\n"
                + "}\n";
    }

    private static String itemModel2612(String id) {
        return "{\n"
                + "  \"model\": {\n"
                + "    \"type\": \"minecraft:model\",\n"
                + "    \"model\": \"" + MOD_ID + ":item/" + id + "\"\n"
                + "  }\n"
                + "}\n";
    }

    /** 该等级的「中间那一格」：铜放原版食物、其余放上一级同类食物。 */
    private static String middleItem(String id, OreFoodTable.ItemKey key, FoodInfo foodInfo) {
        return switch (key.tier().key()) {
            case "copper" -> key.base().baseItemId();
            case "iron" -> MOD_ID + ":copper_" + key.base().key();
            case "golden" -> MOD_ID + ":iron_" + key.base().key();
            case "diamond" -> MOD_ID + ":golden_" + key.base().key();
            default -> MOD_ID + ":diamond_" + key.base().key();
        };
    }


    private static String recipe1201(OreFoodTable.ItemKey key, MineralInfo mineral, FoodInfo foodInfo) {
        String id = key.id();
        if (key.tier().key().equals("netherite")) {
            return "{\n"
                    + "  \"type\": \"minecraft:smithing_transform\",\n"
                    + "  \"addition\": { \"item\": \"" + mineral.material() + "\" },\n"
                    + "  \"base\": { \"item\": \"" + MOD_ID + ":diamond_" + key.base().key() + "\" },\n"
                    + "  \"result\": { \"item\": \"" + MOD_ID + ":" + id + "\" },\n"
                    + "  \"template\": { \"item\": \"" + TEMPLATE_ITEM + "\" }\n"
                    + "}\n";
        }
        return "{\n"
                + "  \"type\": \"minecraft:crafting_shaped\",\n"
                + "  \"category\": \"misc\",\n"
                + "  \"group\": \"ore_food\",\n"
                + "  \"key\": {\n"
                + "    \"#\": { \"item\": \"" + mineral.material() + "\" },\n"
                + "    \"X\": { \"item\": \"" + middleItem(id, key, foodInfo) + "\" }\n"
                + "  },\n"
                + "  \"pattern\": [ \"###\", \"#X#\", \"###\" ],\n"
                + "  \"result\": { \"item\": \"" + MOD_ID + ":" + id + "\" }\n"
                + "}\n";
    }

    private static String recipe1211(OreFoodTable.ItemKey key, MineralInfo mineral, FoodInfo foodInfo) {
        String id = key.id();
        if (key.tier().key().equals("netherite")) {
            return "{\n"
                    + "  \"type\": \"minecraft:smithing_transform\",\n"
                    + "  \"addition\": { \"item\": \"" + mineral.material() + "\" },\n"
                    + "  \"base\": { \"item\": \"" + MOD_ID + ":diamond_" + key.base().key() + "\" },\n"
                    + "  \"result\": { \"id\": \"" + MOD_ID + ":" + id + "\" },\n"
                    + "  \"template\": { \"item\": \"" + TEMPLATE_ITEM + "\" }\n"
                    + "}\n";
        }
        return "{\n"
                + "  \"type\": \"minecraft:crafting_shaped\",\n"
                + "  \"category\": \"misc\",\n"
                + "  \"group\": \"ore_food\",\n"
                + "  \"key\": {\n"
                + "    \"#\": { \"item\": \"" + mineral.material() + "\" },\n"
                + "    \"X\": { \"item\": \"" + middleItem(id, key, foodInfo) + "\" }\n"
                + "  },\n"
                + "  \"pattern\": [ \"###\", \"#X#\", \"###\" ],\n"
                + "  \"result\": { \"id\": \"" + MOD_ID + ":" + id + "\" }\n"
                + "}\n";
    }

    /** 26.1.2 的 Ingredient 只认「纯字符串」，所以材料/模板/基座都写成字符串。 */
    private static String recipe2612(OreFoodTable.ItemKey key, MineralInfo mineral, FoodInfo foodInfo) {
        String id = key.id();
        if (key.tier().key().equals("netherite")) {
            return "{\n"
                    + "  \"type\": \"minecraft:smithing_transform\",\n"
                    + "  \"addition\": \"" + mineral.material() + "\",\n"
                    + "  \"base\": \"" + MOD_ID + ":diamond_" + key.base().key() + "\",\n"
                    + "  \"result\": { \"id\": \"" + MOD_ID + ":" + id + "\" },\n"
                    + "  \"template\": \"" + TEMPLATE_ITEM + "\"\n"
                    + "}\n";
        }
        return "{\n"
                + "  \"type\": \"minecraft:crafting_shaped\",\n"
                + "  \"category\": \"misc\",\n"
                + "  \"group\": \"ore_food\",\n"
                + "  \"key\": {\n"
                + "    \"#\": \"" + mineral.material() + "\",\n"
                + "    \"X\": \"" + middleItem(id, key, foodInfo) + "\"\n"
                + "  },\n"
                + "  \"pattern\": [ \"###\", \"#X#\", \"###\" ],\n"
                + "  \"result\": { \"id\": \"" + MOD_ID + ":" + id + "\" }\n"
                + "}\n";
    }

    private static String tag(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"values\": [\n");
        for (int i = 0; i < ids.size(); i++) {
            sb.append("    \"").append(ids.get(i)).append("\"").append(i == ids.size() - 1 ? "\n" : ",\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 落盘
    // ------------------------------------------------------------------

    private static void writeIfAbsent(Path path, String content, boolean force) throws IOException {
        if (!force && Files.exists(path)) {
            skipped++;
            return;
        }
        writeAlways(path, content);
    }

    private static void writeAlways(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        written++;
        System.out.println("  " + path);
    }

    /** 扁平的语言文件：读出现有键值对，保留顺序，把新的追加到末尾。 */
    private static void mergeLang(Path path, Map<String, String> additions, String locale) throws IOException {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        if (Files.exists(path)) {
            String existing = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(existing);
            while (matcher.find()) {
                entries.put(matcher.group(1), matcher.group(2));
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("语言文件读不出任何键，拒绝重写：" + path);
        }
        for (Map.Entry<String, String> entry : additions.entrySet()) {
            entries.putIfAbsent(entry.getKey(), entry.getValue());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int index = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            sb.append("  \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            sb.append(++index == entries.size() ? "\n" : ",\n");
        }
        sb.append("}\n");
        writeAlways(path, sb.toString());
    }

    private GenerateOreFoodAssets() {
    }
}
