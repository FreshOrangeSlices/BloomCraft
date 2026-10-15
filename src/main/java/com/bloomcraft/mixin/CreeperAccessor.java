package com.bloomcraft.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {

	@Accessor("explosionRadius")
	int bloomcraft$getExplosionRadius();

	@Accessor("explosionRadius")
	void bloomcraft$setExplosionRadius(int radius);

	@Accessor("maxSwell")
	int bloomcraft$getMaxSwell();

	@Accessor("maxSwell")
	void bloomcraft$setMaxSwell(int ticks);
}
