package com.bloomcraft.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A horse's (or donkey's) own inventory: a bonded donkey's chest contents go to its new owner. */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccess {

	@Accessor("inventory")
	SimpleContainer bloomcraft$inventory();
}
