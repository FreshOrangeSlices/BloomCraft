package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;

public final class WardedTrait extends BaseTrait {

	private double blockChance = 50;

	public WardedTrait() {
		super("warded", TraitCategory.DEFENSE, "Warded", "Warded");
	}

	@Override
	public void load(Cfg s) {
		blockChance = s.getDouble("block-chance", 50);
	}

	@Override
	public String description() {
		return Text.num(blockChance) + "% chance to deflect arrows and other projectiles.";
	}

	@Override
	public void onDamaged(Boss boss, DamageContext ctx) {
		if (ctx.projectile() && Rng.chance(Math.min(90.0, blockChance * boss.power()))) {
			ctx.cancel();
			Fx.play(boss.entity(), "item.shield.block", 1.0f, 0.8f);
			Fx.particle(boss.level(), ParticleTypes.ENCHANTED_HIT, Fx.center(boss.entity()), 10, 0.4, 0.1);
		}
	}
}
