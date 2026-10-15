package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.CombatGuard;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

public final class ThornedTrait extends BaseTrait {

	private double reflectPercent = 20;

	public ThornedTrait() {
		super("thorned", TraitCategory.DEFENSE, "Thorned", "Thorned");
	}

	@Override
	public void load(Cfg s) {
		reflectPercent = s.getDouble("reflect-percent", 20);
	}

	@Override
	public String description() {
		return "Reflects " + Text.num(reflectPercent) + "% of melee damage back at the attacker.";
	}

	@Override
	public void afterDamaged(Boss boss, double finalDamage, DamageContext ctx) {
		LivingEntity attacker = ctx.attacker();
		if (!ctx.melee() || ctx.secondary() || attacker == null || attacker == boss.entity()) {
			return;
		}
		double amount = finalDamage * reflectPercent * boss.power() / 100.0;
		if (amount < 0.5) {
			return;
		}
		CombatGuard.damage(attacker, amount, boss.entity(), DamageTypes.THORNS);
		Fx.particle(boss.level(), ParticleTypes.CRIT, Fx.center(attacker), 6, 0.3, 0.1);
	}
}
