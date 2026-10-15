package com.bloomcraft.bosses.trait;

import com.bloomcraft.bosses.Boss;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Base class holding a trait's identity, plus helpers for telegraphed attacks. */
public abstract class BaseTrait implements BossTrait {

	/** Mobs that fly, only swim or never move; movement traits skip these. */
	protected static final Set<EntityType<?>> IMMOBILE_OR_FLYING = Set.of(
		EntityTypes.PHANTOM, EntityTypes.GHAST, EntityTypes.BLAZE, EntityTypes.VEX, EntityTypes.SHULKER,
		EntityTypes.GUARDIAN, EntityTypes.ELDER_GUARDIAN, EntityTypes.WITHER, EntityTypes.BREEZE,
		EntityTypes.HAPPY_GHAST, EntityTypes.CREAKING);

	private final String id;
	private final TraitCategory category;
	private final String displayName;
	private final String adjective;

	protected BaseTrait(String id, TraitCategory category, String displayName, String adjective) {
		this.id = id;
		this.category = category;
		this.displayName = displayName;
		this.adjective = adjective;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public TraitCategory category() {
		return category;
	}

	@Override
	public String displayName() {
		return displayName;
	}

	@Override
	public String adjective() {
		return adjective;
	}

	public static boolean walks(LivingEntity entity) {
		return !IMMOBILE_OR_FLYING.contains(entity.getType());
	}

	// ---------------- tells: readable wind-ups before big attacks ----------------

	/** True while this attack is winding up. */
	protected static boolean telling(Boss boss, String key, int now) {
		String flag = key + ":tell";
		if (!boss.flag(flag)) {
			return false;
		}
		if (now > boss.cooldownUntil(flag) + 40) {
			boss.setFlag(flag, false); // a stale wind-up (the fight moved on): start over next time
			return false;
		}
		return true;
	}

	protected static void startTell(Boss boss, String key, int now, int ticks) {
		boss.setFlag(key + ":tell", true);
		boss.cooldown(key + ":tell", now, ticks);
	}

	/** True once the wind-up is over; clears it so the attack fires exactly once. */
	protected static boolean tellDone(Boss boss, String key, int now) {
		if (!boss.ready(key + ":tell", now)) {
			return false;
		}
		boss.setFlag(key + ":tell", false);
		return true;
	}

	/** A flat ring of coloured dust marking the danger zone of an attack. */
	protected static void ring(ServerLevel level, Vec3 center, double radius, int rgb, float size) {
		int points = Math.max(12, (int) Math.round(radius * 7));
		DustParticleOptions dust = new DustParticleOptions(rgb, size);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(dust, center.x + Math.cos(a) * radius, center.y + 0.15, center.z + Math.sin(a) * radius,
				1, 0, 0, 0, 0);
		}
	}
}
