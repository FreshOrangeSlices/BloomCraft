package com.bloomcraft.mixin;

import com.bloomcraft.bosses.combat.CombatPipeline;
import com.bloomcraft.bosses.item.Statues;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

	/** Every hit on a living entity goes through the boss/relic damage pipeline. */
	@WrapMethod(method = "hurtServer")
	private boolean bloomcraft$hurt(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
		return CombatPipeline.hurt((LivingEntity) (Object) this, level, source, amount, original);
	}

	/** Statues can't be pushed around. */
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$statueStill(CallbackInfoReturnable<Boolean> cir) {
		if (Statues.isStatue((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/** A boss's minions drop nothing. */
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noMinionLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (CombatPipeline.noLoot((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
