package com.bloomcraft.test;

import com.bloomcraft.core.Spawns;
import com.bloomcraft.pets.Blooms;
import com.bloomcraft.pets.Pet;
import com.bloomcraft.pets.PetGuide;
import com.bloomcraft.pets.PetManager;
import com.bloomcraft.pets.Pets;
import com.bloomcraft.pets.Species;
import com.bloomcraft.skills.Skill;
import com.bloomcraft.skills.Skills;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Pets work in a running game: bonding, summoning and putting away, saving, and their safety rules. */
public class PetTest {

	/** Feeds a wild wolf until it bonds, as a player would (sneak + meat). */
	private static PetManager.Active adoptWolf(GameTestHelper helper, ServerPlayer player) {
		Wolf wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(2, 1, 2));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 16));
		PetManager pets = Pets.get().pets();
		int feeds = Pets.get().settings().feedsToTame(Species.WOLF);
		for (int i = 0; i < feeds; i++) {
			pets.bond(player, wolf, Species.WOLF, InteractionHand.MAIN_HAND);
		}
		PetManager.Active a = pets.of(wolf);
		Check.that(helper, a != null, "the wolf didn't bond after " + feeds + " feedings");
		return a;
	}

	@GameTest
	public void wildWolfBecomesAPet(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PetManager.Active a = adoptWolf(helper, player);
		List<Pet> owned = Pets.get().store().pets(player.getUUID());
		Check.that(helper, owned.size() == 1 && owned.getFirst() == a.pet, "not in the owner's pets");
		Check.that(helper, Pets.isPet(a.entity) && Spawns.isTransient(a.entity), "not marked as a pet");
		Check.that(helper, player.getUUID().equals(a.entity.getAttached(Pets.OWNER)), "owner not shared with clients");
		Check.that(helper, a.entity.getMaxHealth() >= 20, "combat pets have at least 10 hearts: " + a.entity.getMaxHealth());
		Check.that(helper, Blooms.hasToy(player), "no Pet Toy");
		Check.that(helper, Skills.get().level(player, Skill.TAMING) >= 2, "no Taming XP for bonding: level "
			+ Skills.get().level(player, Skill.TAMING));
		Check.that(helper, !PetGuide.book().isEmpty(), "pet guide");
		Pets.get().pets().stash(a);
		helper.succeed();
	}

	@GameTest
	public void petGoesIntoItsBloomAndComesBack(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PetManager pets = Pets.get().pets();
		PetManager.Active a = adoptWolf(helper, player);
		Pet pet = a.pet;
		Mob first = a.entity;
		first.setHealth(first.getMaxHealth() / 2);
		pets.dismiss(player, false);
		Check.that(helper, first.isRemoved(), "still in the world after going into its bloom");
		Check.that(helper, pet.snapshot != null, "nothing saved");
		Check.that(helper, pets.summon(player, pet, false), "couldn't summon it again");
		PetManager.Active back = pets.active(player.getUUID());
		Check.that(helper, back != null && back.pet == pet && back.entity != first, "not back");
		Check.near(helper, first.getMaxHealth() / 2, back.entity.getHealth(), 0.6, "health after coming back");
		Check.that(helper, back.entity instanceof Wolf, "came back as " + back.entity.getType());
		// the pet's record survives a save and load
		CompoundTag saved = pet.save(helper.getLevel().registryAccess());
		Pet copy = Pet.load(player.getUUID(), pet.id.toString(), saved, helper.getLevel().registryAccess());
		Check.that(helper, copy != null && copy.name.equals(pet.name) && copy.species == pet.species
			&& copy.level == pet.level && copy.snapshot != null, "record round trip");
		pets.stash(back);
		helper.succeed();
	}

	@GameTest
	public void petsAreSafe(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PetManager.Active a = adoptWolf(helper, player);
		Mob wolf = a.entity;
		float health = wolf.getHealth();
		var sources = helper.getLevel().damageSources();
		Check.that(helper, !wolf.hurtServer(helper.getLevel(), sources.fall(), 6), "fall damage hurt it");
		Check.that(helper, !wolf.hurtServer(helper.getLevel(), sources.playerAttack(player), 6), "its owner hurt it");
		Check.near(helper, health, wolf.getHealth(), 0.01, "health");
		wolf.hurtServer(helper.getLevel(), sources.generic(), 1000);
		Check.that(helper, wolf.isAlive(), "it died instead of fainting");
		Check.that(helper, Pets.get().pets().fainting(a), "not fainting");
		Check.that(helper, a.pet.restingUntil > System.currentTimeMillis(), "not resting after fainting");
		helper.succeed();
	}

	@GameTest
	public void petStorageKeepsItems(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PetManager.Active a = adoptWolf(helper, player);
		ItemStack left = Pets.get().menus().addToStorage(a.pet, new ItemStack(Items.DIAMOND, 70));
		Check.that(helper, left.isEmpty(), "didn't fit: " + left);
		Check.that(helper, a.pet.storage.countItem(Items.DIAMOND) == 70, "storage has " + a.pet.storage.countItem(Items.DIAMOND));
		CompoundTag saved = a.pet.save(helper.getLevel().registryAccess());
		Pet copy = Pet.load(player.getUUID(), a.pet.id.toString(), saved, helper.getLevel().registryAccess());
		Check.that(helper, copy != null && copy.storage.countItem(Items.DIAMOND) == 70, "storage after a save and load");
		Pets.get().pets().stash(a);
		helper.succeed();
	}
}
