package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class PlayerMixin {

	/** The server saw the player jump (relics such as Windstep react to it). */
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void bloomcraft$jumped(CallbackInfo ci) {
		Bosses mod = Bosses.get();
		if (mod != null && mod.settings() != null) {
			mod.relics().onJump((ServerPlayer) (Object) this);
		}
	}
}
