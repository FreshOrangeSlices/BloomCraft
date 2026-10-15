package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class ShadowedTrait extends BaseTrait {

	private double cooldown = 12;
	private double warning = 3;
	private double radius = 6;
	private double seconds = 3;

	public ShadowedTrait() {
		super("shadowed", TraitCategory.CONTROL, "Shadowed", "Shadowed");
	}

	@Override
	public void load(Cfg s) {
		cooldown = Math.max(1, s.getDouble("cooldown-seconds", 12));
		warning = Math.max(0.5, s.getDouble("warning-seconds", 3));
		radius = Math.max(1, s.getDouble("radius", 6));
		seconds = Math.max(0.5, s.getDouble("effect-seconds", 3));
	}

	@Override
	public String description() {
		return "Every " + Text.num(cooldown) + "s dark smoke gathers around it for " + Text.num(warning)
			+ "s, then players within " + Text.num(radius) + " blocks are wrapped in Darkness for " + Text.num(seconds) + "s.";
	}

	@Override
	public void onTick(Boss boss, int now) {
		LivingEntity e = boss.entity();
		if (!boss.inCombat() || !boss.ready("shadow", now)) {
			return;
		}
		int warnTicks = (int) Math.round(warning * 20);
		if (!telling(boss, "shadow", now)) {
			if (boss.nearbyPlayers(radius).isEmpty()) {
				return;
			}
			startTell(boss, "shadow", now, warnTicks);
			Fx.play(e, "entity.warden.nearby_closer", 1.0f, 0.7f);
			Fx.particle(boss.level(), ParticleTypes.SQUID_INK, Fx.center(e), 20, 0.5, 0.02);
			ring(boss.level(), e.position(), radius, 0x2A1B3D, 1.6f);
			return;
		}
		if (!tellDone(boss, "shadow", now)) {
			Fx.particle(boss.level(), ParticleTypes.LARGE_SMOKE, Fx.center(e), 10, 0.8, 0.01);
			ring(boss.level(), e.position(), radius, 0x3B2357, 1.6f);
			return;
		}
		boss.cooldown("shadow", now, (int) Math.round(cooldown * 20));
		Fx.play(e, "entity.warden.sonic_charge", 0.8f, 0.6f);
		Fx.particle(boss.level(), ParticleTypes.SQUID_INK, Fx.center(e), 60, radius / 2.0, 0.05);
		int ticks = (int) Math.round(seconds * 20);
		for (ServerPlayer p : boss.nearbyPlayers(radius)) {
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0), e);
		}
	}
}
