package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.AreaDamage;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class QuakingTrait extends BaseTrait {

	private double cooldown = 9;
	private double warning = 4;
	private double radius = 5;
	private double damage = 4;
	private double knockback = 1.1;

	public QuakingTrait() {
		super("quaking", TraitCategory.CONTROL, "Quaking", "Quaking");
	}

	@Override
	public void load(Cfg s) {
		cooldown = s.getDouble("cooldown-seconds", 9);
		warning = Math.max(0.5, s.getDouble("warning-seconds", 4));
		radius = s.getDouble("radius", 5);
		damage = s.getDouble("damage", 4);
		knockback = s.getDouble("knockback", 1.1);
	}

	@Override
	public String description() {
		return "Every " + Text.num(cooldown) + "s it rears up and a red ring marks the zone for " + Text.num(warning)
			+ "s, then it slams the ground, hitting players within " + Text.num(radius) + " blocks and knocking them away.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return walks(entity);
	}

	@Override
	public void onTick(Boss boss, int now) {
		LivingEntity e = boss.entity();
		if (!boss.inCombat() || !boss.ready("quake", now)) {
			return;
		}
		if (!telling(boss, "quake", now)) {
			// Tell: it rears up, the ground cracks and a red ring marks where the slam will land.
			if (!e.onGround() || boss.nearbyPlayers(radius + 1.5).isEmpty()) {
				return;
			}
			startTell(boss, "quake", now, (int) Math.round(warning * 20));
			Vec3 v = e.getDeltaMovement();
			Motion.set(e, new Vec3(v.x, 0.35, v.z));
			Fx.play(e, "entity.ravager.stunned", 0.9f, 0.6f);
			ring(boss.level(), e.position(), radius, 0xD03020, 1.4f);
			Fx.particle(boss.level(), ParticleTypes.DUST_PLUME, e.position().add(0, 0.1, 0), 12, radius / 3.0, 0.02);
			return;
		}
		if (!tellDone(boss, "quake", now)) {
			boolean soon = boss.cooldownUntil("quake:tell") - now <= 20;
			ring(boss.level(), e.position(), radius, soon ? 0xFF2010 : 0xFF5030, soon ? 2.0f : 1.6f);
			if (soon) {
				Fx.particle(boss.level(), ParticleTypes.DUST_PLUME, e.position().add(0, 0.1, 0), 6, radius / 3.0, 0.02);
			}
			return;
		}
		boss.cooldown("quake", now, (int) Math.round(cooldown * 20));
		Fx.play(e, "entity.generic.explode", 0.7f, 0.6f);
		Fx.particle(boss.level(), ParticleTypes.EXPLOSION, e.position().add(0, 0.2, 0), 4, radius / 3.0, 0);
		Fx.particle(boss.level(), ParticleTypes.CLOUD, e.position().add(0, 0.2, 0), 30, radius / 2.0, 0.05);
		for (ServerPlayer p : boss.nearbyPlayers(radius)) {
			AreaDamage.hit(p, damage * boss.power(), e); // capped: dangerous, not instant
			Vec3 away = Motion.flatDirection(e.position(), p.position()).scale(knockback);
			Motion.set(p, new Vec3(away.x, 0.55, away.z));
		}
	}
}
