package com.bloomcraft.mixin;

import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AgeableMob.class)
public interface AgeableMobInvoker {

	@Invoker("setAgeLocked")
	void bloomcraft$setAgeLocked(boolean locked);
}
