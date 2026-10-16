package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.core.Spawns;
import com.bloomcraft.pets.Pets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityMixin {

	/** Bosses (and pets) can't be put in boats or minecarts; pets are only ridden by their owner, through the toy. */
	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noRiding(Entity vehicle, boolean force, boolean sendEvent, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		Bosses mod = Bosses.get();
		if (mod != null && mod.settings() != null && mod.settings().blockVehicles && mod.bosses().get(self) != null) {
			cir.setReturnValue(false);
		}
		if (Pets.isPet(self) || (Pets.isPet(vehicle) && !Pets.mounting())) {
			cir.setReturnValue(false);
		}
	}

	/** Never-saved entities aren't saved as a vehicle either (a pet you were riding when you left). */
	@Inject(method = "saveAsPassenger", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$transientRide(CallbackInfoReturnable<Boolean> cir) {
		if (Spawns.isTransient((Entity) (Object) this)) {
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
