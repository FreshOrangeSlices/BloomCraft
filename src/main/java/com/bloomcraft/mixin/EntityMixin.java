package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.core.Spawns;
import net.minecraft.server.level.ServerPlayer;
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

	/** Effect mobs (hauntings, chick flocks) are never saved, so a reload can't leave one behind. */
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$transient(CallbackInfoReturnable<Boolean> cir) {
		if (Spawns.isTransient((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/** Some hauntings are only visible to the cursed player. */
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$onlyFor(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		if (!Spawns.visibleTo((Entity) (Object) this, player)) {
			cir.setReturnValue(false);
		}
	}
}
