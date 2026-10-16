package com.bloomcraft.mixin;

import net.minecraft.world.entity.animal.fox.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Wakes a fox up (a fox bonded while napping would otherwise sleep forever, its AI being off). */
@Mixin(Fox.class)
public interface FoxInvoker {

	@Invoker("setSleeping")
	void bloomcraft$setSleeping(boolean sleeping);
}
