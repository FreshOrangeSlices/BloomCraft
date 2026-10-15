package com.bloomcraft.bosses.config;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.reward.GearQuality;
import com.bloomcraft.bosses.reward.GearTier;
import com.bloomcraft.core.Fx;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Everything the config says about one rank. */
public record RankSettings(
	BossRank rank,
	String name,
	Stats stats,
	TraitRoll traits,
	double xpMultiplier,
	int xpBonus,
	Rewards rewards,
	Gear gear,
	boolean persistent,
	Presentation presentation
) {

	/** Multipliers (health, damage, speed), flat bonuses (armor, toughness, knockback resistance, size) and floors. */
	public record Stats(double health, double damage, double armor, double toughness,
						double knockbackResistance, double speed, double size, double minHealth, double minDamage) {
	}

	public record TraitRoll(int min, int max, double power) {
	}

	/** Independent percent chances, rolled separately on death. extraRelics: a 2nd, 3rd... relic. */
	public record Rewards(double bossGear, double empowerment, double relic, List<Double> extraRelics,
						  double relicCatalyst) {
	}

	public record Gear(Map<GearTier, Integer> materials, Map<GearQuality, Integer> quality, int enchantMin,
					   int enchantMax, double minLevelPercent, double overMaxChance, int overMaxLevels) {
	}

	public enum Announce { NONE, ACTIONBAR, CHAT, TITLE }

	public enum Broadcast { NONE, NEARBY, SERVER }

	public record Presentation(Announce announce, Fx.@Nullable Sound sound, Fx.@Nullable Sound deathSound,
							   int particles, Broadcast deathBroadcast) {
	}

	/** "★★★★ Nightmare" */
	public String title() {
		return rank.starText() + " " + name;
	}
}
