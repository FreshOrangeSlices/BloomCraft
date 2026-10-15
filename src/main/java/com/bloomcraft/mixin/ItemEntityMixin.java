package com.bloomcraft.mixin;

import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.core.Data;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {

	/** Boss rewards on the ground can't burn, blow up or be destroyed (only the void takes them). */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void bloomcraft$protectRewards(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) && Data.flag((ItemEntity) (Object) this, BossKeys.PROTECTED_DROP)) {
			cir.setReturnValue(false);
		}
	}
}
