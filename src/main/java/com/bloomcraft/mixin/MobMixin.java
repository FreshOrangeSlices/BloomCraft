package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
abstract class MobMixin {

	/** A mob just spawned: remember how, so it can get its boss roll once it's in the world. */
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void bloomcraft$spawned(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
									SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		Bosses mod = Bosses.get();
		if (mod != null && mod.settings() != null) {
			mod.bosses().handleSpawn((Mob) (Object) this, reason);
		}
	}

	/** Undead bosses don't burn in daylight. */
	@Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noSunburn(CallbackInfo ci) {
		Bosses mod = Bosses.get();
		if (mod != null && mod.bosses().get((Mob) (Object) this) != null) {
			ci.cancel();
		}
	}

	/** Bosses can't be leashed. */
	@Inject(method = "canBeLeashed", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noLeash(CallbackInfoReturnable<Boolean> cir) {
		Bosses mod = Bosses.get();
		if (mod != null && mod.settings() != null && mod.settings().blockVehicles && mod.bosses().get((Mob) (Object) this) != null) {
			cir.setReturnValue(false);
		}
	}
}
