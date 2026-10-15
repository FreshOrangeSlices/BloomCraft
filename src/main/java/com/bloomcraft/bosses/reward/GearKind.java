package com.bloomcraft.bosses.reward;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** The kinds of equipment the Boss Gear roll can produce. */
public enum GearKind {
	SWORD("sword", false, true, null),
	AXE("axe", false, true, null),
	SPEAR("spear", false, true, null),
	MACE(null, false, false, "mace"),
	BOW(null, false, false, "bow"),
	CROSSBOW(null, false, false, "crossbow"),
	TRIDENT(null, false, false, "trident"),
	HELMET("helmet", true, true, null),
	CHESTPLATE("chestplate", true, true, null),
	LEGGINGS("leggings", true, true, null),
	BOOTS("boots", true, true, null),
	PICKAXE("pickaxe", false, true, null),
	SHOVEL("shovel", false, true, null),
	HOE("hoe", false, true, null);

	/** Mining/farming tools (axes count as weapons). */
	public boolean tool() {
		return this == PICKAXE || this == SHOVEL || this == HOE;
	}

	/** The three groups Boss Gear is split into by rank. */
	public enum Category { ARMOR, TOOL, WEAPON }

	public Category category() {
		return armor ? Category.ARMOR : tool() ? Category.TOOL : Category.WEAPON;
	}

	private final @Nullable String suffix;
	private final boolean armor;
	private final boolean tiered;
	private final @Nullable String fixed;

	GearKind(@Nullable String suffix, boolean armor, boolean tiered, @Nullable String fixed) {
		this.suffix = suffix;
		this.armor = armor;
		this.tiered = tiered;
		this.fixed = fixed;
	}

	public boolean armor() {
		return armor;
	}

	/** The actual item, falling back to iron if a tier does not exist for this kind. */
	public Item item(GearTier tier) {
		if (!tiered || suffix == null) {
			return fixed == null ? Items.IRON_SWORD : find(fixed, Items.IRON_SWORD);
		}
		Item item = find(tier.prefix(armor) + "_" + suffix, null);
		return item != null ? item : find("iron_" + suffix, Items.IRON_SWORD);
	}

	private static Item find(String path, @Nullable Item def) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path)).orElse(def);
	}

	public static @Nullable GearKind parse(String raw) {
		String s = raw.trim().toUpperCase(Locale.ROOT);
		for (GearKind kind : values()) {
			if (kind.name().equals(s)) {
				return kind;
			}
		}
		return null;
	}
}
