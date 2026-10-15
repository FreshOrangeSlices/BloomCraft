package com.bloomcraft.bosses.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Deals "secondary" damage (reflections, echoes, lightning bonuses) while a guard is raised. While it's up, on-hit
 * traits and relics don't trigger again, so a Thorned boss fighting a Bramble Heart player can't ping-pong forever.
 */
public final class CombatGuard {

	private static int depth;

	private CombatGuard() {
	}

	public static boolean active() {
		return depth > 0;
	}

	public static void damage(LivingEntity target, double amount, Entity source, ResourceKey<DamageType> type) {
		if (amount <= 0 || !target.isAlive() || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		DamageSource damageSource = new DamageSource(
			level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), source, source);
		depth++;
		try {
			target.hurtServer(level, damageSource, (float) amount);
		} finally {
			depth--;
		}
	}
}
