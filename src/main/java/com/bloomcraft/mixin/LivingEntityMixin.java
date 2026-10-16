package com.bloomcraft.mixin;

import com.bloomcraft.bosses.combat.CombatPipeline;
import com.bloomcraft.bosses.item.Statues;
import com.bloomcraft.pets.PetCombat;
import com.bloomcraft.pets.Pets;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

	/** Every hit on a living entity goes through the pets' rules, then the boss/relic damage pipeline. */
	@WrapMethod(method = "hurtServer")
	private boolean bloomcraft$hurt(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		float changed = PetCombat.before(self, level, source, amount);
		if (changed < 0) {
			return false;
		}
		float before = self.getHealth() + self.getAbsorptionAmount();
		boolean hurt = CombatPipeline.hurt(self, level, source, changed, original);
		if (hurt) {
			float after = self.isDeadOrDying() ? 0 : self.getHealth() + self.getAbsorptionAmount();
			PetCombat.after(self, source, Math.max(0, before - after));
		}
		return hurt;
	}

	/** A Polar Bear's Unyielding (and its owner's Thick Fur): Slowness can't touch them. */
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$petImmunity(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (!Pets.canBeAffected((LivingEntity) (Object) this, effect)) {
			cir.setReturnValue(false);
		}
	}

	/** Pets don't go through portals (they're called to their owner's side instead). */
	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$noPetPortals(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
		if (Pets.isPet((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
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
		if (CombatPipeline.noLoot((LivingEntity) (Object) this) || Pets.isPet((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
