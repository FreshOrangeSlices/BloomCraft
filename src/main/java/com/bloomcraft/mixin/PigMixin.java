package com.bloomcraft.mixin;

import com.bloomcraft.pets.Pets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.pig.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Pig.class)
abstract class PigMixin {

	/** A pet pig struck by lightning stays a pig. */
	@Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$petStaysPig(ServerLevel level, LightningBolt bolt, CallbackInfo ci) {
		if (Pets.isPet((Pig) (Object) this)) {
			ci.cancel();
		}
	}
}
