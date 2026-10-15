package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Off by default: it only made bosses outrun players. */
public final class SwiftTrait extends BaseTrait {

	private double speedBonus = 25;

	public SwiftTrait() {
		super("swift", TraitCategory.MOVEMENT, "Swift", "Swift");
	}

	@Override
	public void load(Cfg s) {
		speedBonus = s.getDouble("speed-bonus", 25);
	}

	@Override
	public String description() {
		return "Moves " + Text.num(speedBonus) + "% faster.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return walks(entity);
	}

	@Override
	public void onApply(Boss boss, boolean fresh) {
		if (fresh && !Attrs.has(boss.entity(), Attributes.MOVEMENT_SPEED, BossKeys.MOD_TRAIT_SWIFT)) {
			Attrs.set(boss.entity(), Attributes.MOVEMENT_SPEED, BossKeys.MOD_TRAIT_SWIFT, speedBonus * boss.power() / 100.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}
}
