package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;

public final class BulwarkTrait extends BaseTrait {

	private double reduction = 20;

	public BulwarkTrait() {
		super("bulwark", TraitCategory.DEFENSE, "Bulwark", "Ironclad");
	}

	@Override
	public void load(Cfg s) {
		reduction = s.getDouble("damage-reduction", 20);
	}

	@Override
	public String description() {
		return "Takes " + Text.num(reduction) + "% less damage from all sources.";
	}

	@Override
	public void onDamaged(Boss boss, DamageContext ctx) {
		double percent = Math.min(60.0, reduction * boss.power());
		ctx.multiply(1.0 - percent / 100.0);
	}
}
