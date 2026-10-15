package com.bloomcraft.bosses.config;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * How strongly a rank's stat bonuses apply to a family of mobs: 1.0 = the full bonus, 0.5 = half, 0 = none.
 * A Creeper, for example, gets half the damage bonus because its explosion already hits hard.
 */
public record MobProfile(String id, Set<EntityType<?>> mobs, double health, double damage, double defense,
						 double speed, double size) {

	public static final MobProfile DEFAULT = new MobProfile("default", Set.of(), 1, 1, 1, 1, 1);

	/** Applies the profile to a multiplier: 1 + (rankValue - 1) * factor. */
	public static double scaleMultiplier(double rankValue, double factor) {
		return 1.0 + (rankValue - 1.0) * factor;
	}
}
