package com.bloomcraft.bosses.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;

/**
 * Collects every damage change from boss traits and relics during one hit, so the damage is only changed once:
 * final = base * multiplier + flat (and at least the floor).
 */
public final class DamageContext {

	private final DamageSource source;
	private final @Nullable LivingEntity attacker;
	private final @Nullable Entity direct;
	private final boolean melee;
	private final boolean projectile;
	private final boolean secondary;
	private double multiplier = 1.0;
	private double flat = 0.0;
	private double floor = 0.0;
	private boolean cancelled;

	private DamageContext(DamageSource source, @Nullable LivingEntity attacker, @Nullable Entity direct, boolean melee,
						  boolean projectile, boolean secondary) {
		this.source = source;
		this.attacker = attacker;
		this.direct = direct;
		this.melee = melee;
		this.projectile = projectile;
		this.secondary = secondary;
	}

	public static DamageContext of(DamageSource source) {
		Entity causing = source.getEntity();
		Entity direct = source.getDirectEntity();
		LivingEntity attacker = causing instanceof LivingEntity living ? living : null;
		boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		boolean melee = attacker != null && direct == causing && !projectile && !source.is(DamageTypeTags.IS_EXPLOSION)
			&& (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
			|| source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING));
		return new DamageContext(source, attacker, direct, melee, projectile, CombatGuard.active());
	}

	public DamageSource source() {
		return source;
	}

	/** The living entity responsible (the shooter for projectiles). */
	public @Nullable LivingEntity attacker() {
		return attacker;
	}

	public @Nullable Entity direct() {
		return direct;
	}

	public boolean melee() {
		return melee;
	}

	public boolean projectile() {
		return projectile;
	}

	/** True for reflected / echoed damage that must not trigger further on-hit effects. */
	public boolean secondary() {
		return secondary;
	}

	public void multiply(double factor) {
		this.multiplier *= factor;
	}

	public void addFlat(double amount) {
		this.flat += amount;
	}

	/** The final damage will be at least this much (a top-rank boss's melee hit). */
	public void floor(double minimum) {
		this.floor = Math.max(this.floor, minimum);
	}

	public void cancel() {
		this.cancelled = true;
	}

	public boolean cancelled() {
		return cancelled;
	}

	public boolean changed() {
		return multiplier != 1.0 || flat != 0.0 || floor > 0.0;
	}

	public double apply(double base) {
		return Math.max(Math.max(0.0, floor), base * multiplier + flat);
	}
}
