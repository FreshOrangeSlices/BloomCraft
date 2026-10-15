package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.world.entity.LivingEntity;

public final class VampiricTrait extends BaseTrait {

	private double healPercent = 25;

	public VampiricTrait() {
		super("vampiric", TraitCategory.OFFENSE, "Vampiric", "Ravenous");
	}

	@Override
	public void load(Cfg s) {
		healPercent = s.getDouble("heal-percent", 25);
	}

	@Override
	public String description() {
		return "Heals for " + Text.num(healPercent) + "% of the damage it deals.";
	}

	@Override
	public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
		if (finalDamage <= 0) {
			return;
		}
		boss.heal(finalDamage * healPercent * boss.power() / 100.0);
		Fx.dust(boss.level(), Fx.center(boss.entity()), 0x8B0000, 1.2f, 6, 0.4);
	}
}
