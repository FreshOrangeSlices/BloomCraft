package com.bloomcraft.client;

import com.bloomcraft.pets.Blooms;
import com.bloomcraft.pets.PetEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/** Client side: HUDs, screens and item visuals. */
public final class BloomCraftClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Punching the air or a block with a Pet Bloom or the Pet Toy opens your pets (a punch on a mob goes to the
		// server as an attack, which the mod turns into the same thing, or into stationing your own pet).
		ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
			ItemStack held = player.getMainHandItem();
			if (!(Blooms.isBloom(held) || Blooms.isToy(held))) {
				return false;
			}
			if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
				return false;
			}
			if (clicks > 0 && ClientPlayNetworking.canSend(PetEvents.OpenPetsPayload.TYPE)) {
				ClientPlayNetworking.send(new PetEvents.OpenPetsPayload());
			}
			return true;
		});
	}
}
