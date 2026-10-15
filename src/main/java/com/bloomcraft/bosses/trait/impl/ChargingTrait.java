package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class ChargingTrait extends BaseTrait {

	private static final String CHARGE_END = "charge_end";
	private double cooldown = 10;
	private double duration = 2.5;
	private double damageBonus = 30;
	private double knockback = 1.0;

	public ChargingTrait() {
		super("charging", TraitCategory.MOVEMENT, "Charging", "Charging");
	}

	@Override
	public void load(Cfg s) {
		cooldown = s.getDouble("cooldown-seconds", 10);
		duration = s.getDouble("duration-seconds", 2.5);
		damageBonus = s.getDouble("damage-bonus", 30);
		knockback = s.getDouble("knockback", 1.0);
	}

	@Override
	public String description() {
		return "Every " + Text.num(cooldown) + "s it charges; a charging hit deals +" + Text.num(damageBonus)
			+ "% damage and heavy knockback.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return walks(entity);
	}

	@Override
	public void onTick(Boss boss, int now) {
		ServerPlayer target = boss.target();
		LivingEntity e = boss.entity();
		if (target == null || !boss.ready("charge", now) || !e.onGround()) {
			return;
		}
		double distance = target.distanceTo(e);
		if (distance < 5 || distance > 18) {
			return;
		}
		int ticks = (int) Math.round(duration * 20);
		e.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 2, false, false));
		boss.cooldown(CHARGE_END, now, ticks);
		boss.cooldown("charge", now, (int) Math.round(cooldown * 20));
		Fx.play(e, "entity.ravager.roar", 0.8f, 1.5f);
		Fx.particle(boss.level(), ParticleTypes.CLOUD, e.position(), 12, 0.4, 0.05);
	}

	private boolean charging(Boss boss) {
		return Ticks.now() < boss.cooldownUntil(CHARGE_END);
	}

	@Override
	public void onAttack(Boss boss, LivingEntity victim, DamageContext ctx) {
		if (ctx.melee() && charging(boss)) {
			ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
		}
	}

	@Override
	public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
		if (!ctx.melee() || !charging(boss)) {
			return;
		}
		LivingEntity e = boss.entity();
		Vec3 push = Motion.flatDirection(e.position(), victim.position()).scale(knockback * 1.2);
		Motion.set(victim, new Vec3(push.x, 0.45 * knockback, push.z));
		e.removeEffect(MobEffects.SPEED);
		boss.cooldown(CHARGE_END, Ticks.now(), 0);
		Fx.play(victim, "entity.player.attack.knockback", 1.0f, 0.7f);
	}
}
