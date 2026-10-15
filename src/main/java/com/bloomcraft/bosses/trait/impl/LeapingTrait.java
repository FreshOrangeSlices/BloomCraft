package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
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

public final class LeapingTrait extends BaseTrait {

	private double cooldown = 6;
	private double minDistance = 4;
	private double maxDistance = 14;
	private double strength = 1.0;

	public LeapingTrait() {
		super("leaping", TraitCategory.MOVEMENT, "Leaping", "Leaping");
	}

	@Override
	public void load(Cfg s) {
		cooldown = s.getDouble("cooldown-seconds", 6);
		minDistance = s.getDouble("min-distance", 4);
		maxDistance = s.getDouble("max-distance", 14);
		strength = s.getDouble("strength", 1.0);
	}

	@Override
	public String description() {
		return "Leaps at targets " + Text.num(minDistance) + "-" + Text.num(maxDistance) + " blocks away (every "
			+ Text.num(cooldown) + "s).";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return walks(entity);
	}

	@Override
	public void onTick(Boss boss, int now) {
		LivingEntity e = boss.entity();
		ServerPlayer target = boss.target();
		if (target == null || !boss.ready("leap", now) || !e.onGround()) {
			return;
		}
		Vec3 diff = target.position().subtract(e.position());
		double distance = diff.length();
		if (distance < minDistance || distance > maxDistance) {
			return;
		}
		if (!telling(boss, "leap", now)) {
			// Tell: it crouches and scrapes the ground for half a second before it jumps.
			startTell(boss, "leap", now, 10);
			Fx.play(e, "entity.goat.prepare_ram", 1.0f, 0.7f);
			Fx.particle(boss.level(), ParticleTypes.DUST_PLUME, e.position().add(0, 0.1, 0), 8, 0.4, 0.02);
			return;
		}
		if (!tellDone(boss, "leap", now)) {
			return;
		}
		Vec3 horizontal = new Vec3(diff.x, 0, diff.z);
		if (horizontal.lengthSqr() < 0.01) {
			return;
		}
		horizontal = horizontal.normalize().scale((0.25 + distance * 0.075) * strength);
		Motion.set(e, new Vec3(horizontal.x, 0.42 + Math.max(0, diff.y) * 0.08 + distance * 0.015, horizontal.z));
		boss.cooldown("leap", now, (int) Math.round(cooldown * 20));
		Fx.play(e, "entity.goat.long_jump", 1.0f, 0.7f);
		Fx.particle(boss.level(), ParticleTypes.CLOUD, e.position(), 10, 0.4, 0.02);
	}
}
