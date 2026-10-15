package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import net.minecraft.world.entity.LivingEntity;

public final class ExecutionerTrait extends BaseTrait {

	private double threshold = 40;
	private double damageBonus = 50;

	public ExecutionerTrait() {
		super("executioner", TraitCategory.OFFENSE, "Executioner", "Merciless");
	}

	@Override
	public void load(Cfg s) {
		threshold = s.getDouble("health-threshold", 40);
		damageBonus = s.getDouble("damage-bonus", 50);
	}

	@Override
	public String description() {
		return "Deals +" + Text.num(damageBonus) + "% damage to targets below " + Text.num(threshold) + "% health.";
	}

	@Override
	public void onAttack(Boss boss, LivingEntity victim, DamageContext ctx) {
		if (victim.getHealth() / Math.max(1, victim.getMaxHealth()) * 100.0 <= threshold) {
			ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
		}
	}
}
