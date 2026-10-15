package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class BlinkingTrait extends BaseTrait {

	private double cooldown = 8;
	private double minDistance = 7;

	public BlinkingTrait() {
		super("blinking", TraitCategory.MOVEMENT, "Blinking", "Phasing");
	}

	@Override
	public void load(Cfg s) {
		cooldown = s.getDouble("cooldown-seconds", 8);
		minDistance = s.getDouble("min-distance", 7);
	}

	@Override
	public String description() {
		return "Teleports behind targets more than " + Text.num(minDistance) + " blocks away, or when shot (every "
			+ Text.num(cooldown) + "s).";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return walks(entity);
	}

	@Override
	public void onTick(Boss boss, int now) {
		ServerPlayer target = boss.target();
		if (target != null && boss.ready("blink", now) && target.distanceToSqr(boss.entity()) > minDistance * minDistance) {
			blink(boss, target, now);
		}
	}

	@Override
	public void afterDamaged(Boss boss, double finalDamage, DamageContext ctx) {
		int now = Ticks.now();
		if (ctx.projectile() && ctx.attacker() instanceof ServerPlayer shooter && boss.fightable(shooter)
			&& boss.ready("blink", now) && shooter.distanceToSqr(boss.entity()) < 48 * 48) {
			blink(boss, shooter, now);
		}
	}

	private void blink(Boss boss, ServerPlayer target, int now) {
		SafeSpots.Spot spot = SafeSpots.behind(target, 2.2);
		if (spot == null) {
			boss.cooldown("blink", now, 20);
			return;
		}
		LivingEntity e = boss.entity();
		Fx.particle(boss.level(), ParticleTypes.PORTAL, Fx.center(e), 30, 0.5, 0.4);
		Fx.play(e, "entity.enderman.teleport", 1.0f, 0.9f);
		e.snapTo(spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), e.getXRot());
		e.setYHeadRot(spot.yaw());
		Fx.particle(boss.level(), ParticleTypes.PORTAL, Fx.center(e), 30, 0.5, 0.4);
		Fx.play(e, "entity.enderman.teleport", 1.0f, 1.1f);
		boss.cooldown("blink", now, (int) Math.round(cooldown * 20));
	}
}
