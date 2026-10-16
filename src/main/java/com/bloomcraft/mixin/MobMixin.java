package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.item.Statues;
import com.bloomcraft.pets.Pets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

	/** Undead bosses (and statues) don't burn in daylight. */
	@Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noSunburn(CallbackInfo ci) {
		Bosses mod = Bosses.get();
		Mob self = (Mob) (Object) this;
		if (mod != null && (mod.bosses().get(self) != null || Statues.isStatue(self))) {
			ci.cancel();
		}
	}

	/** Statues stay, even in Peaceful. */
	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$statuesStay(CallbackInfo ci) {
		if (Statues.isStatue((Mob) (Object) this) || Pets.isPet((Mob) (Object) this)) {
			ci.cancel();
		}
	}

	/** A prowling Nemesis is busy hunting other mobs: it leaves players alone unless they attack it. */
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$prowling(LivingEntity target, CallbackInfo ci) {
		if (!(target instanceof ServerPlayer player)) {
			return;
		}
		if (!Pets.mayTarget((Mob) (Object) this, player)) {
			ci.cancel(); // a Fox makes you harder to notice; Cats keep creepers and phantoms away
			return;
		}
		Bosses mod = Bosses.get();
		if (mod == null || mod.settings() == null) {
			return;
		}
		Boss boss = mod.bosses().get((Mob) (Object) this);
		if (boss != null && boss.isNemesis() && !mod.nemesis().mayTarget(boss, player)) {
			ci.cancel();
		}
	}

	/** A pet's own AI is off: the pets part steers it every tick (it still moves, falls and swims). */
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$steeredPet(CallbackInfo ci) {
		if (Pets.steered((Mob) (Object) this)) {
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
		if (Pets.isPet((Mob) (Object) this)) {
			cir.setReturnValue(false); // pets follow you without a lead
		}
	}
}
