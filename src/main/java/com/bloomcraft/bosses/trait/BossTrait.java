package com.bloomcraft.bosses.trait;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.core.Cfg;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Set;

/**
 * A boss combat trait: it changes how a boss fights rather than just raising its numbers.
 *
 * <p>Hooks are called by the shared managers: {@link #onTick} every 10 ticks; {@link #onAttack}/{@link #onDamaged}
 * before damage is applied (change the DamageContext); {@link #afterAttack}/{@link #afterDamaged} once the damage
 * is final.</p>
 */
public interface BossTrait {

	String id();

	TraitCategory category();

	/** Name shown in the guide and /bosses inspect, e.g. "Vampiric". */
	String displayName();

	/** Word used in the boss name, e.g. "Ravenous" in "Nightmare Ravenous Zombie". */
	String adjective();

	/** One or two sentences, using the configured numbers. */
	String description();

	default Set<String> incompatibleWith() {
		return Set.of();
	}

	default boolean supports(LivingEntity entity) {
		return true;
	}

	/** Reads this trait's settings (traits.&lt;id&gt;). */
	default void load(Cfg section) {
	}

	/** {@code fresh}: the boss was just made (false when it's restored after a chunk load). */
	default void onApply(Boss boss, boolean fresh) {
	}

	default void onTick(Boss boss, int now) {
	}

	default void onAttack(Boss boss, LivingEntity victim, DamageContext ctx) {
	}

	default void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
	}

	default void onDamaged(Boss boss, DamageContext ctx) {
	}

	default void afterDamaged(Boss boss, double finalDamage, DamageContext ctx) {
	}

	/** A projectile was launched by the boss; its velocity can still be changed. */
	default void onProjectileLaunch(Boss boss, Projectile projectile) {
	}

	/** After every trait's onProjectileLaunch, when the velocity is final. */
	default void afterProjectileLaunch(Boss boss, Projectile projectile) {
	}

	/** Return true to cheat death. */
	default boolean onLethalDamage(Boss boss) {
		return false;
	}

	default void onDeath(Boss boss) {
	}
}
