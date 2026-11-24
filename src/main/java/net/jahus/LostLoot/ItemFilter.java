package net.jahus.LostLoot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Item / tag filter with optional per-rule and global rarity allow-lists.
 *
 * <p>Config shapes:
 * <ul>
 *   <li>{@code "rarity": ["uncommon", "epic"]} - global: stack rarity must be in the list
 *       (empty / omitted = no global rarity gate)</li>
 *   <li>{@code "item_filter": [ "minecraft:dirt", "#minecraft:flowers",
 *       {"id": "minecraft:iron_axe", "rarity": ["epic"]} ]}</li>
 * </ul>
 *
 * <p>A bare string matches any rarity. An object matches only if the stack rarity is
 * listed (when {@code rarity} is non-empty). Include/exclude then applies to that match.
 */
public final class ItemFilter {

    public enum Mode {
        NONE, INCLUDE, EXCLUDE;

        static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) return NONE;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "include" -> INCLUDE;
                case "exclude" -> EXCLUDE;
                default -> NONE;
            };
        }

        public String configName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One resolved filter rule (item or tag + optional rarity set). */
    public static final class Rule {
        /** Original config id, e.g. {@code minecraft:dirt} or {@code #minecraft:flowers}. */
        public final String rawId;
        public final Item item;                 // null if tag rule
        public final TagKey<Item> tag;          // null if item rule
        /** Empty = any rarity. */
        public final Set<Rarity> rarities;

        Rule(String rawId, Item item, TagKey<Item> tag, Set<Rarity> rarities) {
            this.rawId = rawId;
            this.item = item;
            this.tag = tag;
            this.rarities = rarities;
        }

        boolean isTag() {
            return tag != null;
        }
    }

    /** Empty = no global rarity gate. */
    public static Set<Rarity> globalRarities = Set.of();
    public static Mode mode = Mode.NONE;
    /** Keep original JSON-ish entries for save (strings or structured). */
    public static List<RawEntry> rawEntries = List.of();

    private static List<Rule> resolvedRules = List.of();
    private static boolean resolved = false;

    /** Unresolved config entry (string id or id + rarity list). */
    public static final class RawEntry {
        public final String id;
        /** Empty = no per-entry rarity constraint. */
        public final List<String> rarityNames;

        public RawEntry(String id, List<String> rarityNames) {
            this.id = id;
            this.rarityNames = rarityNames == null ? List.of() : List.copyOf(rarityNames);
        }

        public static RawEntry ofId(String id) {
            return new RawEntry(id, List.of());
        }
    }

    private ItemFilter() {}

    public static void invalidate() {
        resolved = false;
    }

    /** Parse one rarity name; invalid → null. */
    public static Rarity parseRarity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> null;
        };
    }

    /** Parse a list of rarity names into a set; skips unknowns with a warning. */
    public static Set<Rarity> parseRarityList(List<String> names) {
        if (names == null || names.isEmpty()) return Set.of();
        EnumSet<Rarity> set = EnumSet.noneOf(Rarity.class);
        for (String name : names) {
            Rarity r = parseRarity(name);
            if (r == null) {
                LostLoot.LOGGER.warn("[ItemFilter] Unknown rarity '{}', ignored", name);
                continue;
            }
            set.add(r);
        }
        return set.isEmpty() ? Set.of() : Set.copyOf(set);
    }

    public static String rarityConfigName(Rarity r) {
        return r.name().toLowerCase(Locale.ROOT);
    }

    private static void ensureResolved() {
        if (resolved) return;
        List<Rule> rules = new ArrayList<>();
        for (RawEntry entry : rawEntries) {
            if (entry == null || entry.id == null || entry.id.isBlank()) continue;
            String s = entry.id.trim();
            Set<Rarity> rarities = parseRarityList(entry.rarityNames);

            if (s.startsWith("#")) {
                Identifier id = Identifier.tryParse(s.substring(1));
                if (id == null) {
                    LostLoot.LOGGER.warn("[ItemFilter] Invalid tag id: {}", s);
                    continue;
                }
                rules.add(new Rule(s, null, TagKey.create(Registries.ITEM, id), rarities));
            } else {
                Identifier id = Identifier.tryParse(s);
                if (id == null) {
                    LostLoot.LOGGER.warn("[ItemFilter] Invalid item id: {}", s);
                    continue;
                }
                Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(id);
                if (opt.isEmpty() || opt.get() == Items.AIR) {
                    LostLoot.LOGGER.warn("[ItemFilter] Unknown item: {}", s);
                    continue;
                }
                rules.add(new Rule(s, opt.get(), null, rarities));
            }
        }
        resolvedRules = List.copyOf(rules);
        resolved = true;
        LostLoot.LOGGER.info("[ItemFilter] mode={} rules={} globalRarities={}",
                mode.configName(), resolvedRules.size(), globalRarities.size());
    }

    private static boolean rarityOk(Set<Rarity> allowed, Rarity stackRarity) {
        return allowed.isEmpty() || allowed.contains(stackRarity);
    }

    private static boolean ruleMatches(Rule rule, ItemStack stack) {
        Item item = stack.getItem();
        boolean idMatch;
        if (rule.item != null) {
            idMatch = rule.item == item;
        } else {
            idMatch = BuiltInRegistries.ITEM.wrapAsHolder(item).is(rule.tag);
        }
        if (!idMatch) return false;
        return rarityOk(rule.rarities, stack.getRarity());
    }

    public static boolean allows(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // Global rarity allow-list first
        if (!rarityOk(globalRarities, stack.getRarity())) return false;

        if (mode == Mode.NONE) return true;

        ensureResolved();
        boolean matched = false;
        for (Rule rule : resolvedRules) {
            if (ruleMatches(rule, stack)) {
                matched = true;
                break;
            }
        }
        return (mode == Mode.INCLUDE) == matched;
    }
}
