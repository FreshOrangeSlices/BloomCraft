package com.bloomcraft.mixin;

import net.minecraft.world.entity.monster.Vex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A pet Vex lives as long as any other pet (a summoned Vex normally fades after a while). */
@Mixin(Vex.class)
public interface VexAccess {

	@Accessor("hasLimitedLife")
	void bloomcraft$setHasLimitedLife(boolean limited);
}
