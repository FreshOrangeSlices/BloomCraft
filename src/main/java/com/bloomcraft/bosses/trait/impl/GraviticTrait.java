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

public final class GraviticTrait extends BaseTrait {

	private double cooldown = 8;
	private double radius = 10;
	private double strength = 0.9;

	public GraviticTrait() {
		super("gravitic", TraitCategory.CONTROL, "Gravitic", "Gravitic");
	}

	@Override
	public void load(Cfg s) {
		cooldown = s.getDouble("cooldown-seconds", 8);
		radius = s.getDouble("radius", 10);
		strength = s.getDouble("strength", 0.9);
	}

	@Override
	public String description() {
		return "Every " + Text.num(cooldown) + "s it hums and a violet ring appears, then it pulls players within "
			+ Text.num(radius) + " blocks toward itself.";
	}

	@Override
	public void onTick(Boss boss, int now) {
		if (!boss.inCombat() || !boss.ready("gravity", now)) {
			return;
		}
		LivingEntity e = boss.entity();
		if (!telling(boss, "gravity", now)) {
			// Tell: a violet ring and a hum before the pull.
			boolean someoneFar = false;
			for (ServerPlayer p : boss.nearbyPlayers(radius)) {
				if (p.distanceToSqr(e) >= 9) {
					someoneFar = true;
					break;
				}
			}
			if (!someoneFar) {
				return;
			}
			startTell(boss, "gravity", now, 20);
			Fx.play(e, "block.beacon.deactivate", 1.0f, 0.5f);
			ring(boss.level(), e.position(), radius, 0x8A2BE2, 1.3f);
			Fx.particle(boss.level(), ParticleTypes.REVERSE_PORTAL, Fx.center(e), 30, 0.6, 0.02);
			return;
		}
		if (!tellDone(boss, "gravity", now)) {
			ring(boss.level(), e.position(), radius * 0.6, 0xB060FF, 1.3f);
			return;
		}
		boss.cooldown("gravity", now, (int) Math.round(cooldown * 20));
		for (ServerPlayer p : boss.nearbyPlayers(radius)) {
			Vec3 diff = e.position().subtract(p.position());
			double distance = diff.length();
			if (distance < 3) {
				continue;
			}
			Vec3 pull = diff.normalize().scale(Math.min(1.6, strength * (0.4 + distance * 0.08)));
			Motion.set(p, new Vec3(pull.x, Math.max(0.25, pull.y), pull.z));
			Fx.particle(boss.level(), ParticleTypes.REVERSE_PORTAL, Fx.center(p), 15, 0.3, 0.05);
		}
		Fx.play(e, "block.respawn_anchor.deplete", 1.0f, 0.6f);
		Fx.particle(boss.level(), ParticleTypes.REVERSE_PORTAL, Fx.center(e), 40, 1.2, 0.1);
	}
}
