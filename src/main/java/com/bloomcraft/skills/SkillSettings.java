package com.bloomcraft.skills;

import com.bloomcraft.core.Cfg;

import java.util.EnumMap;
import java.util.Map;

/** Everything skills.yml says. */
public final class SkillSettings {

	public final int maxLevel;
	public final double curveBase;
	public final double curvePerLevel;
	public final double xpMultiplier;
	private final Map<Skill, Boolean> enabled = new EnumMap<>(Skill.class);
	private final Map<Skill, Double> multipliers = new EnumMap<>(Skill.class);
	// Taming XP
	public final double tamingHitMultiplier;
	public final double tamingHostileValue;
	public final double tamingAnimalValue;
	public final double tamingOtherValue;
	public final double tamingPlayerValue;
	public final double tamingNewPet;
	public final double tamingNewSpecies;
	public final double tamingPetLevelUp;

	public SkillSettings(Cfg c) {
		maxLevel = Math.max(1, Math.min(1000, c.getInt("skills.max-level", 100)));
		curveBase = Math.max(1, c.getDouble("skills.xp-curve.base", 200));
		curvePerLevel = Math.max(0, c.getDouble("skills.xp-curve.per-level", 40));
		xpMultiplier = Math.max(0, c.getDouble("skills.xp-multiplier", 1.0));
		for (Skill s : Skill.values()) {
			enabled.put(s, c.getBool(s.id() + ".enabled", true));
			multipliers.put(s, Math.max(0, c.getDouble(s.id() + ".xp-multiplier", 1.0)));
		}
		tamingHitMultiplier = Math.max(0, c.getDouble("taming.xp.pet-hit-multiplier", 3));
		tamingHostileValue = Math.max(0, c.getDouble("taming.xp.mob-values.hostile", 2.0));
		tamingAnimalValue = Math.max(0, c.getDouble("taming.xp.mob-values.animal", 1.0));
		tamingOtherValue = Math.max(0, c.getDouble("taming.xp.mob-values.other", 1.0));
		tamingPlayerValue = Math.max(0, c.getDouble("taming.xp.mob-values.player", 4.0));
		tamingNewPet = Math.max(0, c.getDouble("taming.xp.new-pet", 150));
		tamingNewSpecies = Math.max(0, c.getDouble("taming.xp.new-species", 300));
		tamingPetLevelUp = Math.max(0, c.getDouble("taming.xp.pet-level-up", 40));
	}

	public boolean enabled(Skill skill) {
		return enabled.getOrDefault(skill, true);
	}

	public double multiplier(Skill skill) {
		return multipliers.getOrDefault(skill, 1.0) * xpMultiplier;
	}

	/** XP to go from {@code level} to the next one. */
	public double xpToNext(int level) {
		return curveBase + curvePerLevel * Math.max(0, level);
	}
}
