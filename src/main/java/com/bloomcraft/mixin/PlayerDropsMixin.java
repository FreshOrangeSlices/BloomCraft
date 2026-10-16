package com.bloomcraft.mixin;

import com.bloomcraft.pets.Pets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerDropsMixin {

	/** Your Pet Blooms, Pet Toy and Fetch Stick stay with you when you die (they come back when you respawn). */
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void bloomcraft$keepPetItems(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			Pets.keepPetItems(player);
		}
	}
}
