package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import com.bloomcraft.mixin.CreeperAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

public final class VolatileTrait extends BaseTrait {

	private double radiusBonus = 2;
	private double fuseReduction = 30;

	public VolatileTrait() {
		super("volatile", TraitCategory.SPECIAL, "Volatile", "Volatile");
	}

	@Override
	public void load(Cfg s) {
		radiusBonus = s.getDouble("explosion-radius-bonus", 2);
		fuseReduction = s.getDouble("fuse-reduction", 30);
	}

	@Override
	public String description() {
		return "Creepers only. Explosion radius +" + Text.num(radiusBonus) + " and a " + Text.num(fuseReduction)
			+ "% shorter fuse.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return entity instanceof Creeper;
	}

	@Override
	public void onApply(Boss boss, boolean fresh) {
		if (!fresh || !(boss.entity() instanceof Creeper creeper)) {
			return; // radius and fuse are saved with the creeper, so they're only set once
		}
		CreeperAccessor c = (CreeperAccessor) creeper;
		c.bloomcraft$setExplosionRadius(c.bloomcraft$getExplosionRadius() + (int) Math.round(radiusBonus * boss.power()));
		c.bloomcraft$setMaxSwell(Math.max(10, (int) Math.round(c.bloomcraft$getMaxSwell() * (1.0 - fuseReduction / 100.0))));
	}
}
