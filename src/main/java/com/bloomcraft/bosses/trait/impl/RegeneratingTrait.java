package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;

public final class RegeneratingTrait extends BaseTrait {

	private double percentPerSecond = 1.5;
	private double delaySeconds = 4;

	public RegeneratingTrait() {
		super("regenerating", TraitCategory.DEFENSE, "Regenerating", "Regenerating");
	}

	@Override
	public void load(Cfg s) {
		percentPerSecond = s.getDouble("heal-percent-per-second", 1.5);
		delaySeconds = s.getDouble("delay-seconds", 4);
	}

	@Override
	public String description() {
		return "If not hurt for " + Text.num(delaySeconds) + "s, it heals " + Text.num(percentPerSecond)
			+ "% of its max health per second.";
	}

	@Override
	public void onTick(Boss boss, int now) {
		if (boss.healthRatio() >= 1.0 || now - boss.lastHurtTick() < delaySeconds * 20) {
			return;
		}
		// onTick runs every 10 ticks = half a second
		boss.heal(boss.maxHealth() * percentPerSecond * boss.power() / 100.0 / 2.0);
		if (now % 40 < 10) {
			Fx.particle(boss.level(), ParticleTypes.HAPPY_VILLAGER, Fx.center(boss.entity()), 3, 0.5, 0);
		}
	}
}
