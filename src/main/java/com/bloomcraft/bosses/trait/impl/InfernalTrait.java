package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

public final class InfernalTrait extends BaseTrait {

	private double seconds = 4;

	public InfernalTrait() {
		super("infernal", TraitCategory.SPECIAL, "Infernal", "Infernal");
	}

	@Override
	public void load(Cfg s) {
		seconds = s.getDouble("ignite-seconds", 4);
	}

	@Override
	public String description() {
		return "Immune to fire and lava; its hits set targets ablaze for " + Text.num(seconds) + "s.";
	}

	private static boolean fire(DamageContext ctx) {
		var s = ctx.source();
		return s.is(DamageTypes.IN_FIRE) || s.is(DamageTypes.ON_FIRE) || s.is(DamageTypes.LAVA)
			|| s.is(DamageTypes.HOT_FLOOR) || s.is(DamageTypes.CAMPFIRE);
	}

	@Override
	public void onDamaged(Boss boss, DamageContext ctx) {
		if (fire(ctx)) {
			ctx.cancel();
		}
	}

	@Override
	public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
		if (ctx.secondary() || finalDamage <= 0) {
			return;
		}
		int ticks = (int) Math.round(seconds * 20 * boss.power());
		victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), ticks));
	}

	@Override
	public void onTick(Boss boss, int now) {
		// it always burns (its own fire can't hurt it)
		LivingEntity e = boss.entity();
		e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 40));
		if (now % 20 < 10) {
			Fx.particle(boss.level(), ParticleTypes.FLAME, Fx.center(e), 3, 0.35, 0.01);
		}
	}
}
