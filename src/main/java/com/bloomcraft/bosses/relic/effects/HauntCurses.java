package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.bosses.relic.RelicManager;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Spawns;
import com.bloomcraft.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hauntings and mischief: Withering Waters, Uninvited Guest (the wandering trader jump scare), Don't Blink
 * (a weeping angel), Poltergeist, Restless Dead and Hiccups.
 *
 * <p>Every mob these curses spawn is tagged as a minion (no drops, never a boss) and is never saved, so a reload
 * can't leave one behind.</p>
 */
public final class HauntCurses {

	public static final String GUEST = "uninvited-guest";
	static final String ANGEL = "dont-blink";
	static final String RISEN = "restless-dead";

	private HauntCurses() {
	}

	private static @Nullable Entity find(MinecraftServer server, @Nullable UUID id) {
		if (id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------

	/** Like an Enderman: rain and water wither you. */
	public static final class WitheringWaters extends BaseRelic {
		private int amplifier = 0;
		private double seconds = 3;

		public WitheringWaters() {
			super("withering-waters", "Withering Waters", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			amplifier = Math.max(0, Math.min(4, s.getInt("level", 1) - 1));
			seconds = Math.max(1, s.getDouble("seconds", 3));
		}

		@Override
		public String description() {
			return "Like an Enderman, water hurts you: rain and water wither you.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (!p.isInWaterOrRain() || p.isCreative() || p.isSpectator()) {
				return;
			}
			// Only top it up when it's about to run out: Wither hurts on a rhythm of its remaining time, and
			// resetting it every second would skip every hurting tick.
			MobEffectInstance current = p.getEffect(MobEffects.WITHER);
			if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= 20) {
				p.addEffect(new MobEffectInstance(MobEffects.WITHER, Math.max(60, (int) Math.round(seconds * 20)), amplifier,
					false, true));
			}
			hiss(p, ctx.manager());
		}

		/** Test: a few seconds of the withering, as if standing in the rain. */
		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, amplifier, false, true));
			hiss(p, manager);
			return true;
		}

		private void hiss(ServerPlayer p, RelicManager manager) {
			Fx.particle(p.level(), ParticleTypes.SMOKE, p.position().add(0, 1, 0), 6, 0.3, 0.01);
			if (manager.ready(p, id(), 60)) {
				Fx.playToAt(p, "block.fire.extinguish", p.position(), 0.6f, 1.4f);
			}
		}
	}

	// ------------------------------------------------------------------

	/**
	 * The wandering trader jump scare: an ominous cave noise, then a trader only you can see walks up behind you,
	 * a shriek, and he's gone. About three seconds from start to finish.
	 */
	public static final class UninvitedGuest extends BaseRelic {
		/** Player -> the trader visiting them right now. */
		private final Map<UUID, UUID> visiting = new HashMap<>();
		private double chance = 1;
		private double cooldown = 480;

		public UninvitedGuest() {
			super(GUEST, "Uninvited Guest", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance-per-second", 1);
			cooldown = s.getDouble("cooldown-seconds", 480);
		}

		@Override
		public String description() {
			return "Every now and then, a wandering trader is standing right behind you. Only you can see him.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (visiting.containsKey(p.getUUID()) || p.isPassenger() || p.getAbilities().flying || p.isFallFlying()
				|| p.isSpectator() || !Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				return;
			}
			visit(p);
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			return !visiting.containsKey(p.getUUID()) && visit(p);
		}

		private boolean visit(ServerPlayer p) {
			SafeSpots.Spot spot = SafeSpots.behind(p, 5);
			if (spot == null) {
				return false;
			}
			ServerLevel level = p.level();
			Fx.playToAt(p, "ambient.cave", RelicUtil.behind(p, 6), 1.0f, 0.7f);
			WanderingTrader trader = Spawns.effectMob(level, EntityTypes.WANDERING_TRADER, spot.pos(), spot.yaw(), GUEST, t -> {
				Spawns.onlyVisibleTo(t, p); // only the cursed player ever sees him
				t.setPermanentlyInvulnerable(true); // (which also makes other mobs ignore him)
				t.setSilent(true);
				t.getGoalSelector().removeAllGoals(g -> true); // no wandering off or drinking potions: he only walks to you
			});
			if (trader == null) {
				return false;
			}
			UUID id = p.getUUID();
			visiting.put(id, trader.getUUID());
			MinecraftServer server = level.getServer();
			int[] tick = {0};
			int[] scaredAt = {-1};
			Scheduler.repeat(1, 1, () -> {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player == null || !trader.isAlive() || player.level() != trader.level() || tick[0] > 80) {
					end(player, trader, id);
					return false;
				}
				trader.getLookControl().setLookAt(player, 30f, 30f);
				if (scaredAt[0] < 0) {
					if (tick[0] % 5 == 0) {
						trader.getNavigation().moveTo(player, 1.0);
					}
					double distSq = trader.distanceToSqr(player);
					if (distSq < 1.7 * 1.7 || tick[0] >= 45) {
						if (distSq > 2.5 * 2.5) {
							// Running away doesn't help: he's simply there.
							SafeSpots.Spot close = SafeSpots.behind(player, 1.3);
							if (close != null) {
								trader.teleportTo(close.pos().x, close.pos().y, close.pos().z);
								trader.setYRot(close.yaw());
								trader.setYHeadRot(close.yaw());
							}
						}
						scare(player, trader);
						scaredAt[0] = tick[0];
					}
				} else if (tick[0] - scaredAt[0] >= 20) {
					end(player, trader, id);
					return false;
				}
				tick[0]++;
				return true;
			});
			return true;
		}

		private void end(@Nullable ServerPlayer player, WanderingTrader trader, UUID id) {
			visiting.remove(id, trader.getUUID());
			if (!trader.isAlive()) {
				return;
			}
			Vec3 at = trader.position().add(0, 1, 0);
			if (player != null && player.level() == trader.level()) {
				Fx.particleTo(player, ParticleTypes.POOF, at, 12, 0.3, 0.02);
				Fx.playToAt(player, "entity.wandering_trader.disappeared", at, 0.8f, 1.0f);
			}
			trader.discard();
		}

		/** Logout or curse removed: the guest leaves at once. */
		@Override
		public void onDeactivate(ServerPlayer player) {
			Entity trader = find(player.level().getServer(), visiting.remove(player.getUUID()));
			if (trader != null) {
				trader.discard();
			}
		}

		private static void scare(ServerPlayer player, WanderingTrader trader) {
			Vec3 at = trader.position();
			Fx.playToAt(player, "block.sculk_shrieker.shriek", at, 1.0f, 1.0f);
			Fx.playToAt(player, "entity.wandering_trader.ambient", at, 1.0f, 0.55f);
			RelicUtil.darkness(player, 30);
		}
	}

	// ------------------------------------------------------------------

	/**
	 * A weeping angel: in the dark a Creaking follows you. It only moves while nobody looks at it, can't be hurt,
	 * and crumbles if you stare it down for a few seconds (or after a while on its own).
	 */
	public static final class DontBlink extends BaseRelic {
		private static final class Angel {
			final UUID entity;
			int age;
			int stare;

			Angel(UUID entity) {
				this.entity = entity;
			}
		}

		private final Map<UUID, Angel> angels = new HashMap<>();
		private double chance = 1.5;
		private double cooldown = 300;
		private int lifetime = 45;
		private int stareSeconds = 4;

		public DontBlink() {
			super(ANGEL, "Don't Blink", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance-per-second", 1.5);
			cooldown = s.getDouble("cooldown-seconds", 300);
			lifetime = Math.max(5, s.getInt("lifetime-seconds", 45));
			stareSeconds = Math.max(1, s.getInt("stare-seconds", 4));
		}

		@Override
		public String description() {
			return "In the dark, something follows you. It only moves when you aren't looking."
				+ " Stare at it for " + stareSeconds + " seconds and it crumbles.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			Angel angel = angels.get(p.getUUID());
			if (angel != null) {
				watch(p, angel);
				return;
			}
			if (!dark(p) || p.isPassenger() || p.isSpectator() || p.isCreative() || !Rng.chance(chance)
				|| !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				return;
			}
			summon(p);
		}

		/** Test: it appears behind you whatever the light (it still only watches over cursed players). */
		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			Angel old = angels.get(p.getUUID());
			if (old != null) {
				vanish(p, p.level().getEntity(old.entity), false);
			}
			if (!summon(p)) {
				return false;
			}
			// Someone without the curse isn't watched over every second, so make sure it leaves on time anyway.
			UUID entity = angels.get(p.getUUID()).entity;
			Scheduler.later(lifetime * 20, () -> {
				Angel angel = angels.get(p.getUUID());
				if (angel != null && angel.entity.equals(entity)) {
					vanish(p, find(p.level().getServer(), entity), false);
				}
			});
			return true;
		}

		private boolean summon(ServerPlayer p) {
			ServerLevel level = p.level();
			Vec3 spot = null;
			for (int attempt = 0; attempt < 6 && spot == null; attempt++) {
				spot = SafeSpots.standable(level, RelicUtil.behind(p, Rng.between(10.0, 14.0)), 4);
			}
			if (spot == null) {
				return false;
			}
			Creaking creaking = Spawns.effectMob(level, EntityTypes.CREAKING, spot, SafeSpots.yawTowards(spot, p.position()),
				ANGEL, c -> c.setPermanentlyInvulnerable(true));
			if (creaking == null) {
				return false;
			}
			creaking.activate(p);
			angels.put(p.getUUID(), new Angel(creaking.getUUID()));
			Fx.playToAt(p, "block.sculk_shrieker.shriek", spot, 0.45f, 0.7f);
			return true;
		}

		private static boolean dark(ServerPlayer p) {
			return RelicUtil.isNight(p.level()) || RelicUtil.light(p.level(), p.blockPosition()) <= 7;
		}

		private void watch(ServerPlayer p, Angel angel) {
			Entity e = p.level().getEntity(angel.entity);
			angel.age++;
			if (e == null || !e.isAlive() || e.distanceToSqr(p) > 48 * 48 || angel.age > lifetime) {
				vanish(p, e != null ? e : find(p.level().getServer(), angel.entity), false);
				return;
			}
			if (e instanceof Creaking creaking && !creaking.isActive()) {
				creaking.activate(p);
			}
			if (lookingAt(p, e)) {
				angel.stare++;
				if (angel.stare >= stareSeconds) {
					vanish(p, e, true);
				}
			} else {
				angel.stare = 0;
			}
		}

		private static boolean lookingAt(ServerPlayer p, Entity e) {
			Vec3 eye = p.getEyePosition();
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(eye);
			if (to.lengthSqr() < 0.01) {
				return true;
			}
			if (to.lengthSqr() > 40 * 40) {
				return false;
			}
			Vec3 view = p.getViewVector(1.0f);
			double cos = view.dot(to) / (view.length() * to.length());
			return Math.acos(Math.max(-1, Math.min(1, cos))) < 0.4 && p.hasLineOfSight(e);
		}

		private void vanish(ServerPlayer p, @Nullable Entity e, boolean crumbled) {
			angels.remove(p.getUUID());
			if (e == null || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 at = e.position().add(0, 1, 0);
			if (crumbled) {
				Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PALE_OAK_LOG.defaultBlockState()), at,
					40, 0.3, 0.8, 0.3, 0);
				Fx.play(level, at, "entity.creaking.death", 1.0f, 0.8f);
			} else {
				Fx.particle(level, ParticleTypes.SMOKE, at, 20, 0.3, 0.8, 0.3, 0.01);
			}
			e.discard();
		}

		@Override
		public void onDeactivate(ServerPlayer player) {
			Angel angel = angels.remove(player.getUUID());
			if (angel != null) {
				Entity e = find(player.level().getServer(), angel.entity);
				if (e != null) {
					e.discard();
				}
			}
		}
	}

	// ------------------------------------------------------------------

	/** Shuffles your hotbar and opens nearby doors (they close again by themselves). */
	public static final class Poltergeist extends BaseRelic {
		private double chance = 2;
		private double cooldown = 60;

		public Poltergeist() {
			super("poltergeist", "Poltergeist", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance-per-second", 2);
			cooldown = s.getDouble("cooldown-seconds", 60);
		}

		@Override
		public String description() {
			return "Something mischievous shuffles your hotbar and opens doors around you.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (Rng.chance(chance) && ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				trigger(p, ctx.manager());
			}
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			if (!(Rng.chance(50) && rattleDoor(p))) {
				shuffle(p);
			}
			return true;
		}

		private static void shuffle(ServerPlayer p) {
			Inventory inv = p.getInventory();
			int a = Rng.between(0, 8);
			int b = Rng.between(0, 7);
			if (b >= a) {
				b++;
			}
			ItemStack first = inv.getItem(a);
			ItemStack second = inv.getItem(b);
			if (first.isEmpty() && second.isEmpty()) {
				return;
			}
			inv.setItem(a, second);
			inv.setItem(b, first);
			Fx.playToAt(p, "entity.vex.ambient", RelicUtil.behind(p, 2), 0.8f, 1.3f);
			Fx.actionBar(p, Component.literal("Something rummages through your pockets...").withStyle(ChatFormatting.DARK_PURPLE));
		}

		/** Opens a closed wooden door near the player for a moment. False if there is none. */
		private static boolean rattleDoor(ServerPlayer p) {
			ServerLevel level = p.level();
			BlockPos origin = p.blockPosition();
			List<BlockPos> doors = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -2, -6), origin.offset(6, 2, 6))) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof DoorBlock && DoorBlock.isWoodenDoor(state)
					&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !state.getValue(DoorBlock.OPEN)) {
					doors.add(pos.immutable());
				}
			}
			if (doors.isEmpty()) {
				return false;
			}
			BlockPos door = Rng.pick(doors);
			if (!setOpen(level, door, true)) {
				return false;
			}
			Scheduler.later(30, () -> setOpen(level, door, false));
			return true;
		}

		/** Opens or closes the door (both halves, with its sound). False if it isn't a door any more. */
		private static boolean setOpen(ServerLevel level, BlockPos pos, boolean open) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof DoorBlock door) || state.getValue(DoorBlock.OPEN) == open) {
				return false;
			}
			door.setOpen(null, level, state, pos, open);
			return true;
		}
	}

	// ------------------------------------------------------------------

	/** Undead you kill sometimes get back up for round two (they drop nothing the second time). */
	public static final class RestlessDead extends BaseRelic {
		private static final Set<EntityType<?>> UNDEAD = Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED,
			EntityTypes.ZOMBIE_VILLAGER, EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED,
			EntityTypes.WITHER_SKELETON, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.ZOGLIN, EntityTypes.PHANTOM);
		private double chance = 30;

		public RestlessDead() {
			super(RISEN, "Restless Dead", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance", 30);
		}

		@Override
		public String description() {
			return Text.num(chance) + "% of the undead you kill climb back up for round two.";
		}

		@Override
		public void onKill(RelicContext ctx, LivingEntity dead, boolean victimWasBoss) {
			if (victimWasBoss || !UNDEAD.contains(dead.getType()) || BossManager.isMinion(dead)
				|| !ctx.manager().ready(ctx.player(), id() + ":kill", 1) || !Rng.chance(chance)) {
				return; // (the cooldown: the curse on two items still raises one mob per kill)
			}
			raise(ctx.player(), dead.getType(), dead.position(), dead.isBaby());
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			Vec3 at = SafeSpots.standable(p.level(), RelicUtil.behind(p, 3), 3);
			raise(p, EntityTypes.ZOMBIE, at != null ? at : p.position(), false);
			return true;
		}

		private static void raise(ServerPlayer p, EntityType<?> type, Vec3 at, boolean baby) {
			ServerLevel level = p.level();
			Fx.particle(level, ParticleTypes.SOUL, at.add(0, 0.3, 0), 8, 0.4, 0.02);
			UUID id = p.getUUID();
			Scheduler.later(40, () -> {
				if (!level.hasChunkAt(BlockPos.containing(at))) {
					return;
				}
				Entity risen = Spawns.effectMob(level, type, at, Rng.r().nextFloat() * 360f, RISEN, e -> {
					if (e instanceof Mob mob) {
						mob.setBaby(baby);
					}
				});
				if (risen == null) {
					return;
				}
				Fx.particle(level, ParticleTypes.SOUL, at.add(0, 1, 0), 15, 0.4, 0.05);
				Fx.play(level, at, "entity.zombie_villager.converted", 0.8f, 0.6f);
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (risen instanceof Mob mob && player != null && player.level() == level) {
					mob.setTarget(player);
				}
			});
		}
	}

	// ------------------------------------------------------------------

	/** Hic! Every so often you hop into the air. */
	public static final class Hiccups extends BaseRelic {
		private double chance = 4;
		private double cooldown = 8;

		public Hiccups() {
			super("hiccups", "Hiccups", true);
		}

		@Override
		public double defaultWeight() {
			return 6;
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance-per-second", 4);
			cooldown = s.getDouble("cooldown-seconds", 8);
		}

		@Override
		public String description() {
			return "Hic! Every so often you hop into the air.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (!p.onGround() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || p.isSwimming()
				|| !Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				return;
			}
			trigger(p, ctx.manager());
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			Vec3 v = p.getDeltaMovement();
			Motion.set(p, new Vec3(v.x, 0.4, v.z));
			Fx.play(p, "entity.player.burp", 0.6f, 1.7f);
			Fx.actionBar(p, Component.literal("*hic*").withStyle(ChatFormatting.GRAY));
			return true;
		}
	}

	/** True in the Overworld at night (for curses that like the dark). */
	static boolean night(Level level) {
		return RelicUtil.isNight(level);
	}
}
