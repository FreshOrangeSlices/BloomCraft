package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Rng;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Curses about the living world: Pariah (friendly creatures fear you) and Herbivore (no meat for you). */
public final class NatureCurses {

	public static final Set<Item> MEAT = Set.of(
		Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.CHICKEN, Items.COOKED_CHICKEN,
		Items.MUTTON, Items.COOKED_MUTTON, Items.RABBIT, Items.COOKED_RABBIT, Items.RABBIT_STEW, Items.COD,
		Items.COOKED_COD, Items.SALMON, Items.COOKED_SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH, Items.ROTTEN_FLESH);
	public static final Set<Item> FISH = Set.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH);
	private static final Item[] PLANT_FOOD = {Items.COAL, Items.WHEAT_SEEDS, Items.SWEET_BERRIES};

	private NatureCurses() {
	}

	/** Animals flee, villagers charge more, and fish won't bite: only treasure does. */
	public static final class Pariah extends BaseRelic {
		private double radius = 12;
		private int villagerPenalty = 40;
		private double fleeSpeed = 2.0;
		private int speedLevel = 3;

		public Pariah() {
			super("pariah", "Pariah", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			radius = s.getDouble("radius", 12);
			fleeSpeed = Math.max(1.0, s.getDouble("flee-speed", 2.0));
			speedLevel = Math.max(0, Math.min(5, s.getInt("speed-level", 3)));
			// Iron golems turn on players around -100 reputation, so stay below that.
			villagerPenalty = Math.max(0, Math.min(99, s.getInt("villager-price-penalty", 40)));
		}

		@Override
		public String description() {
			return "Animals bolt from you, far faster than you can run. Villagers charge you more."
				+ " Fish won't bite your hook, but treasure does.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		/**
		 * Once a second: nearby animals bolt. They get a burst of speed (faster than a sprinting player) and leap
		 * clear if you get close, so you basically can't catch one.
		 */
		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (p.isSpectator()) {
				return;
			}
			Vec3 from = p.position();
			AABB box = p.getBoundingBox().inflate(radius, 4, radius);
			for (Animal animal : p.level().getEntitiesOfClass(Animal.class, box, a -> a.isAlive() && wild(a))) {
				Vec3 away = new Vec3(animal.getX() - from.x, 0, animal.getZ() - from.z);
				if (away.lengthSqr() < 0.01) {
					away = new Vec3(Rng.between(-1.0, 1.0), 0, Rng.between(-1.0, 1.0));
				}
				double distance = away.length();
				away = away.normalize();
				if (speedLevel > 0) {
					animal.addEffect(new MobEffectInstance(MobEffects.SPEED, 50, speedLevel - 1, false, false));
				}
				if (distance < 4 && animal.onGround()) {
					Motion.set(animal, new Vec3(away.x * 0.8, 0.35, away.z * 0.8));
				}
				if (distance < radius * 0.6 || animal.getNavigation().isDone()) {
					Vec3 to = animal.position().add(away.scale(radius));
					animal.getNavigation().moveTo(to.x, to.y, to.z, fleeSpeed);
				}
			}
		}

		/** Animals that bolt: not tamed, leashed, ridden, pets or hostile (hoglins are animals too). */
		public static boolean wild(Animal animal) {
			return !(animal instanceof Enemy) && !(animal instanceof TamableAnimal t && t.isTame()) && !animal.isLeashed()
				&& !animal.isVehicle() && !BossManager.isPet(animal);
		}

		/** Villagers remember a Pariah: worse prices (like after hitting one, but not enough to anger golems). */
		public void sour(Villager villager, ServerPlayer player) {
			if (villagerPenalty <= 0) {
				return;
			}
			var entries = villager.getGossips().getGossipEntries().get(player.getUUID());
			int current = entries == null ? 0 : entries.getInt(GossipType.MINOR_NEGATIVE);
			if (current < villagerPenalty) {
				villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, villagerPenalty - current);
			}
			Fx.particle((ServerLevel) villager.level(), ParticleTypes.ANGRY_VILLAGER, villager.getEyePosition().add(0, 0.4, 0),
				3, 0.3, 0);
		}
	}

	/** No meat or fish: it rots into coal, seeds or berries the moment you carry it. */
	public static final class Herbivore extends BaseRelic {

		public Herbivore() {
			super("herbivore", "Herbivore", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public String description() {
			return "You can't eat meat or fish. Any you carry turns into coal, seeds or berries.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			convertMeat(ctx.player());
		}
	}

	/** Swaps every stack of meat or fish in the player's inventory for coal, seeds or berries. */
	public static void convertMeat(ServerPlayer p) {
		Inventory inv = p.getInventory();
		boolean changed = false;
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack item = inv.getItem(slot);
			if (!item.isEmpty() && MEAT.contains(item.getItem())) {
				// Rotten flesh never becomes coal, so a zombie farm doesn't turn into a coal farm.
				int from = item.is(Items.ROTTEN_FLESH) ? 1 : 0;
				inv.setItem(slot, new ItemStack(PLANT_FOOD[Rng.between(from, PLANT_FOOD.length - 1)], item.getCount()));
				changed = true;
			}
		}
		if (changed) {
			Fx.actionBar(p, Component.literal("Your meat withers away...").withStyle(ChatFormatting.DARK_GREEN));
			Fx.playTo(p, "block.composter.fill_success", 0.8f, 0.8f);
		}
	}
}
