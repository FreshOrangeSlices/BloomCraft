package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityMixin {

	/** Bosses can't be put in boats or minecarts. */
	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noRiding(Entity vehicle, boolean force, boolean sendEvent, CallbackInfoReturnable<Boolean> cir) {
		Bosses mod = Bosses.get();
		if (mod != null && mod.settings() != null && mod.settings().blockVehicles
			&& mod.bosses().get((Entity) (Object) this) != null) {
			cir.setReturnValue(false);
		}
	}
}
