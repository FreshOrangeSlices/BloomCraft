package com.bloomcraft.bosses.combat;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.core.Ticks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.bloomcraft.core.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The single damage pipeline for bosses and relics, wrapped around LivingEntity#hurtServer:
 * <ol>
 *   <li>before: every change (boss rank damage, traits, relics) is collected in one DamageContext and applied once;</li>
 *   <li>after: reactions to the damage actually taken (lifesteal, reflection, on-hit effects, engagement, boss bar).</li>
 * </ol>
 */
public final class CombatPipeline {

	private CombatPipeline() {
	}

	private static boolean ignored(DamageSource source) {
		return source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
			|| source.is(DamageTypes.OUTSIDE_BORDER);
	}

	public static boolean hurt(LivingEntity victim, ServerLevel level, DamageSource source, float amount,
							   Operation<Boolean> original) {
		Bosses mod = Bosses.get();
		if (mod == null || mod.settings() == null || ignored(source)) {
			return original.call(level, source, amount);
		}
		BossSettings s = mod.settings();
		if (source.is(DamageTypes.IN_WALL) && s.breakFreeWhenStuck && mod.bosses().get(victim) != null
			&& breakFree(victim, level)) {
			return false; // a boss stuck in blocks tears its way out instead of suffocating
		}
		DamageContext ctx = DamageContext.of(source);
		LivingEntity attacker = ctx.attacker();
		Boss attackerBoss = attacker == null || attacker == victim ? null : mod.bosses().get(attacker);
		Boss victimBoss = mod.bosses().get(victim);
		// An area attack deals its own fixed amount: no rank multiplier, no minimum hit and no on-hit traits,
		// then the cap, so it's dangerous but never instant.
		boolean area = AreaDamage.active() && victim instanceof ServerPlayer && attackerBoss != null;

		if (attackerBoss != null && !ctx.secondary() && !area) {
			ctx.multiply(attackerBoss.damageMultiplier());
			if (ctx.melee()) {
				ctx.floor(s.rank(attackerBoss.rank()).stats().minDamage());
			}
			for (BossTrait trait : attackerBoss.traits()) {
				trait.onAttack(attackerBoss, victim, ctx);
			}
		}
		if (victimBoss != null) {
			victimBoss.markHurt(Ticks.now());
			for (BossTrait trait : victimBoss.traits()) {
				trait.onDamaged(victimBoss, ctx);
			}
		}
		if (attacker instanceof ServerPlayer player && attacker != victim && !ctx.secondary()) {
			mod.relics().onAttack(player, victim, ctx, victimBoss != null);
		}
		if (victim instanceof ServerPlayer player) {
			mod.relics().onDamaged(player, ctx);
		}
		double overflow = victim instanceof ServerPlayer player && attackerBoss != null ? overflowArmor(s, player) : 1.0;
		if (victimBoss != null && victimBoss.isNemesis() && !ctx.secondary()) {
			mod.nemesis().onHurt(victimBoss, ctx); // what it has adapted to hurts it less (or not at all)
		}
		if (attackerBoss != null && attackerBoss.isNemesis() && BossManager.isPet(victim)) {
			mod.nemesis().onHitPet(attackerBoss, ctx);
		}

		if (ctx.cancelled()) {
			return false;
		}
		double damage = amount;
		if (ctx.changed() || area || overflow < 1.0) {
			damage = ctx.changed() ? ctx.apply(amount) : amount;
			if (area) {
				damage = Math.min(damage, s.areaDamageCap); // area attacks: dangerous, never instant
			}
			damage *= overflow; // overflow armor last, so it helps against capped hits too
		}

		float before = victim.getHealth() + victim.getAbsorptionAmount();
		boolean hurt = original.call(level, source, (float) damage);
		if (!hurt) {
			return false;
		}
		float after = victim.isDeadOrDying() ? 0 : victim.getHealth() + victim.getAbsorptionAmount();
		double finalDamage = Math.max(0, before - after);
		if (victim instanceof ServerPlayer player && finalDamage > 0 && mod.waystones().isTravelling(player.getUUID())) {
			mod.waystones().cancelTravel(player.getUUID(), true); // taking damage breaks the waystone's pull
		}
		afterDamage(mod, victim, ctx, attacker, attackerBoss, victimBoss, finalDamage);
		return true;
	}

	/**
	 * Breaks the blocks around a suffocating boss's eyes (like the game's own check: not the floor it stands on).
	 * Unbreakable and very hard blocks, containers and budding amethyst are left alone; mob griefing must be on.
	 */
	private static boolean breakFree(LivingEntity boss, ServerLevel level) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		AABB box = boss.getBoundingBox();
		double eye = boss.getEyeY();
		double half = boss.getBbWidth() * 0.4;
		boolean freed = false;
		for (int x = (int) Math.floor(boss.getX() - half); x <= (int) Math.floor(boss.getX() + half); x++) {
			for (int y = (int) Math.floor(Math.max(box.minY + 0.6, eye - 0.3)); y <= (int) Math.floor(eye + 0.2); y++) {
				for (int z = (int) Math.floor(boss.getZ() - half); z <= (int) Math.floor(boss.getZ() + half); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					float hardness = state.getDestroySpeed(level, pos);
					if (state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty() || hardness < 0
						|| hardness >= 50 || state.hasBlockEntity() || state.is(Blocks.BUDDING_AMETHYST)) {
						continue;
					}
					freed |= level.destroyBlock(pos, true, boss, 512);
				}
			}
		}
		if (freed) {
			Fx.play(boss, "entity.zombie.break_wooden_door", 0.8f, 0.8f);
		}
		return freed;
	}

	/** Armor past 20 keeps paying off against bosses: 5% less damage from them per point over. */
	private static double overflowArmor(BossSettings s, ServerPlayer player) {
		if (!s.overflowArmor) {
			return 1.0;
		}
		double over = player.getAttributeValue(Attributes.ARMOR) - s.overflowArmorFrom;
		return over > 0 ? Math.max(0, 1 - over * s.overflowArmorPerPoint / 100.0) : 1.0;
	}

	private static void afterDamage(Bosses mod, LivingEntity victim, DamageContext ctx, LivingEntity attacker,
									 Boss attackerBoss, Boss victimBoss, double finalDamage) {
		if (victim instanceof ServerPlayer hurt && attacker != null && (attackerBoss != null || BossManager.isMinion(attacker))) {
			mod.nemesis().markBossHit(hurt); // (a death a boss set up never counts as a surrender)
		}
		if (attackerBoss != null) {
			if (victim instanceof ServerPlayer player) {
				mod.bosses().engage(attackerBoss, player);
			}
			if (!ctx.secondary() && !AreaDamage.active()) {
				for (BossTrait trait : attackerBoss.traits()) {
					trait.afterAttack(attackerBoss, victim, finalDamage, ctx);
				}
			}
		}
		if (victimBoss != null) {
			if (attacker instanceof ServerPlayer player) {
				if (victimBoss.isNemesis()) {
					mod.nemesis().onProvoked(victimBoss, player);
				}
				mod.bosses().engage(victimBoss, player);
				victimBoss.recordPlayerHit(player.getUUID(), finalDamage, ctx.projectile());
			}
			if (victimBoss.isNemesis()) {
				mod.nemesis().recordDamage(victimBoss, ctx.source(), attacker, finalDamage);
			}
			if (victim.isAlive()) {
				for (BossTrait trait : victimBoss.traits()) {
					trait.afterDamaged(victimBoss, finalDamage, ctx);
				}
				mod.bosses().checkLastStand(victimBoss);
				mod.bosses().checkPhases(victimBoss);
				mod.bossBars().updateHealth(victimBoss);
			}
		}
		if (attacker instanceof ServerPlayer player && attacker != victim && !ctx.secondary()) {
			mod.relics().afterAttack(player, victim, finalDamage, ctx);
		}
		if (victim instanceof ServerPlayer player) {
			mod.relics().afterDamaged(player, finalDamage, ctx);
		}
	}

	/** A minion's drops and experience: none. */
	public static boolean noLoot(LivingEntity entity) {
		return BossManager.isMinion(entity);
	}
}
