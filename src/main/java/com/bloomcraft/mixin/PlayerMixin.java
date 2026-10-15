package com.bloomcraft.mixin;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerMixin {

	/** The server saw the player jump (relics such as Windstep react to it). */
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void bloomcraft$jumped(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			Bosses mod = Bosses.get();
			if (mod != null && mod.settings() != null) {
				mod.relics().onJump(player);
			}
		}
	}
}
