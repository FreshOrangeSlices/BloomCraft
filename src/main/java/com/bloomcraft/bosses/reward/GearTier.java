package com.bloomcraft.bosses.reward;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Material tier of a Boss Gear item. Weights per rank are in the config (ranks.X.gear.materials). */
public enum GearTier {
	CHAINMAIL("iron", "chainmail"), // chainmail only exists as armor; weapons fall back to iron
	COPPER("copper", "copper"),
	IRON("iron", "iron"),
	GOLD("golden", "golden"),
	DIAMOND("diamond", "diamond"),
	NETHERITE("netherite", "netherite");

	private final String toolPrefix;
	private final String armorPrefix;

	GearTier(String toolPrefix, String armorPrefix) {
		this.toolPrefix = toolPrefix;
		this.armorPrefix = armorPrefix;
	}

	public String prefix(boolean armor) {
		return armor ? armorPrefix : toolPrefix;
	}

	public static @Nullable GearTier parse(String raw) {
		String s = raw.trim().toUpperCase(Locale.ROOT);
		if (s.equals("GOLDEN")) {
			return GOLD;
		}
		for (GearTier tier : values()) {
			if (tier.name().equals(s)) {
				return tier;
			}
		}
		return null;
	}
}
