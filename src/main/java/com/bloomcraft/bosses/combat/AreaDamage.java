package com.bloomcraft.bosses.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Damage from a boss's area attacks (Quaking's slam, Ascendant shockwaves). It goes through the normal damage
 * handling but never hits a player harder than the configured cap: dangerous, not instant.
 */
public final class AreaDamage {

	private static int depth;

	private AreaDamage() {
	}

	public static void hit(LivingEntity player, double amount, LivingEntity source) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		depth++;
		try {
			player.hurtServer(level, level.damageSources().mobAttack(source), (float) amount);
		} finally {
			depth--;
		}
	}

	/** True while an area attack's damage is being dealt. */
	public static boolean active() {
		return depth > 0;
	}
}
