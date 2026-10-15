package com.bloomcraft.bosses;

import com.bloomcraft.bosses.config.Messages;
import com.bloomcraft.bosses.config.RankSettings;
import com.bloomcraft.core.Fx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Encounter presentation: spawn announcements, first-engagement cues, ambient particles and death effects.
 * Higher ranks get stronger cues, but nothing runs every tick and nothing is spammed.
 */
public final class Presentation {

	private final Bosses bosses;

	public Presentation(Bosses bosses) {
		this.bosses = bosses;
	}

	private RankSettings.Presentation of(Boss boss) {
		return bosses.settings().rank(boss.rank()).presentation();
	}

	private Messages messages() {
		return bosses.settings().messages;
	}

	public Component rankTitle(BossRank rank) {
		return rank.styled(bosses.settings().rank(rank).title());
	}

	private static Map<String, Component> values(Object... pairs) {
		Map<String, Component> map = new HashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			Object v = pairs[i + 1];
			map.put((String) pairs[i], v instanceof Component c ? c : Component.literal(String.valueOf(v)));
		}
		return map;
	}

	/** Shown to players near a boss right after it appears naturally. */
	public void announceSpawn(Boss boss) {
		RankSettings.Presentation p = of(boss);
		if (p.announce() == RankSettings.Announce.NONE) {
			return;
		}
		double radius = bosses.settings().announceRadius;
		LivingEntity e = boss.entity();
		for (ServerPlayer player : boss.level().players()) {
			if (player.distanceToSqr(e) > radius * radius) {
				continue;
			}
			Map<String, Component> v = values("boss", boss.name(), "rank", rankTitle(boss.rank()));
			switch (p.announce()) {
				case ACTIONBAR -> Fx.actionBar(player, messages().get("spawn-actionbar", v));
				case CHAT -> {
					if (bosses.settings().bossChat) {
						player.sendSystemMessage(messages().prefixed("spawn-chat", v));
					} else {
						Fx.actionBar(player, messages().get("spawn-actionbar", v));
					}
				}
				case TITLE -> {
					Fx.title(player, messages().get("spawn-title", v), messages().get("spawn-subtitle", v), 6, 56, 18);
					if (bosses.settings().bossChat) {
						player.sendSystemMessage(messages().prefixed("spawn-chat", v));
					}
				}
				default -> {
				}
			}
			Fx.playTo(player, p.sound());
		}
	}

	/** The first time a player engages a boss: its rank cue plays for them, plus a one-time burst. */
	public void reveal(Boss boss, ServerPlayer player) {
		if (boss.revealTo(player)) {
			Fx.playTo(player, of(boss).sound());
		}
		if (boss.revealOnce()) {
			LivingEntity e = boss.entity();
			int burst = 6 + boss.rank().stars() * 6;
			Fx.dust(boss.level(), Fx.center(e), boss.rank().rgb(), 1.6f, burst, 0.7);
			if (boss.rank().top()) {
				Fx.particle(boss.level(), ParticleTypes.END_ROD, Fx.center(e), boss.rank() == BossRank.ASCENDANT ? 50 : 20, 0.6, 0.05);
			}
		}
	}

	/** About once per second for every loaded boss. Subtle by design. */
	public void ambient(Boss boss) {
		LivingEntity e = boss.entity();
		ServerLevel level = boss.level();
		Vec3 at = Fx.center(e);
		int count = of(boss).particles();
		if (count > 0) {
			Fx.dust(level, at, boss.rank().rgb(), 1.1f, count, Math.max(0.3, e.getBbWidth() * 0.6));
			if (boss.rank() == BossRank.GOLD) {
				Fx.particle(level, ParticleTypes.WAX_ON, at, 1, 0.5, 0);
			} else if (boss.rank() == BossRank.ASCENDANT) {
				Fx.particle(level, ParticleTypes.END_ROD, at, 2, 0.6, 0.01);
				Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, at.add(0, e.getBbHeight() * 0.6, 0), 2, 0.5, 0.05);
			}
		}
		if (bosses.settings().environmentalParticles) {
			environment(level, e, at);
		}
		int scars = boss.isNemesis() ? bosses.nemesis().scarLevel(boss) : 0;
		if (scars > 0) {
			Fx.particle(level, ParticleTypes.LARGE_SMOKE, at, scars, 0.3, 0.01);
			Fx.dust(level, at.add(0, e.getBbHeight() * 0.4, 0), 0xFFFFFF, 1.0f, 2 + scars, 0.25);
			if (scars >= 4) {
				Fx.particle(level, ParticleTypes.SOUL, at, 1, 0.4, 0.01);
			}
		}
		if (boss.lastStand()) {
			Fx.particle(level, ParticleTypes.SOUL_FIRE_FLAME, at, 3, 0.4, 0.02);
		}
	}

	/** Flavour that fits the mob: Blazes smoulder, Drowned drip, skeletons shed bone dust... */
	private static void environment(ServerLevel level, LivingEntity e, Vec3 at) {
		double spread = Math.max(0.25, e.getBbWidth() * 0.5);
		EntityType<?> t = e.getType();
		if (t == EntityTypes.BLAZE) {
			spawn(level, ParticleTypes.FLAME, at, 3, spread, 0.4, 0.01);
		} else if (t == EntityTypes.MAGMA_CUBE) {
			spawn(level, ParticleTypes.DRIPPING_LAVA, at, 2, spread, 0.2, 0);
		} else if (t == EntityTypes.DROWNED || t == EntityTypes.GUARDIAN || t == EntityTypes.ELDER_GUARDIAN) {
			spawn(level, ParticleTypes.BUBBLE_POP, at, 3, spread, 0.4, 0.02);
			spawn(level, ParticleTypes.DRIPPING_WATER, at, 2, spread, 0.3, 0);
		} else if (t == EntityTypes.SKELETON || t == EntityTypes.PARCHED) {
			spawn(level, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), at, 2, spread, 0.4, 0.02);
		} else if (t == EntityTypes.STRAY) {
			spawn(level, ParticleTypes.SNOWFLAKE, at, 2, spread, 0.4, 0.01);
		} else if (t == EntityTypes.BOGGED) {
			spawn(level, ParticleTypes.FALLING_SPORE_BLOSSOM, at, 1, spread, 0.4, 0);
		} else if (t == EntityTypes.WITHER_SKELETON) {
			spawn(level, ParticleTypes.SMOKE, at, 2, spread, 0.5, 0.01);
			spawn(level, ParticleTypes.SOUL, at, 1, spread, 0.5, 0.01);
		} else if (t == EntityTypes.ZOMBIE || t == EntityTypes.ZOMBIE_VILLAGER) {
			spawn(level, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH)), at, 1, spread, 0.4, 0.02);
		} else if (t == EntityTypes.HUSK) {
			spawn(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), at, 2, spread, 0.4, 0);
		} else if (t == EntityTypes.SPIDER || t == EntityTypes.CAVE_SPIDER) {
			spawn(level, ParticleTypes.ITEM_COBWEB, at, 1, spread, 0.2, 0);
		} else if (t == EntityTypes.CREEPER) {
			spawn(level, ParticleTypes.SMOKE, at, 1, spread, 0.3, 0.01);
		} else if (t == EntityTypes.ENDERMAN || t == EntityTypes.ENDERMITE) {
			spawn(level, ParticleTypes.PORTAL, at, 4, spread, 0.8, 0.2);
		} else if (t == EntityTypes.WITCH || t == EntityTypes.EVOKER || t == EntityTypes.ILLUSIONER) {
			spawn(level, ParticleTypes.WITCH, at, 2, spread, 0.4, 0);
		} else if (t == EntityTypes.PIGLIN || t == EntityTypes.PIGLIN_BRUTE || t == EntityTypes.ZOMBIFIED_PIGLIN
			|| t == EntityTypes.HOGLIN || t == EntityTypes.ZOGLIN) {
			spawn(level, ParticleTypes.ASH, at, 3, spread, 0.4, 0);
		} else if (t == EntityTypes.GHAST) {
			spawn(level, ParticleTypes.WHITE_ASH, at, 4, spread, 0.6, 0);
		} else if (t == EntityTypes.PHANTOM) {
			spawn(level, ParticleTypes.MYCELIUM, at, 3, spread, 0.3, 0);
		} else if (t == EntityTypes.BREEZE) {
			spawn(level, ParticleTypes.SMALL_GUST, at, 1, spread, 0.3, 0);
		} else if (t == EntityTypes.SLIME) {
			spawn(level, ParticleTypes.ITEM_SLIME, at, 2, spread, 0.3, 0);
		} else if (t == EntityTypes.SILVERFISH) {
			spawn(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState()), at, 1, spread, 0.2, 0);
		} else if (t == EntityTypes.VINDICATOR || t == EntityTypes.PILLAGER || t == EntityTypes.RAVAGER) {
			spawn(level, ParticleTypes.CRIT, at, 1, spread, 0.4, 0.05);
		}
	}

	private static void spawn(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double dy, double speed) {
		Fx.particle(level, particle, at, count, spread, dy, spread, speed);
	}

	public void death(Boss boss, @Nullable ServerPlayer killer) {
		RankSettings.Presentation p = of(boss);
		LivingEntity e = boss.entity();
		ServerLevel level = boss.level();
		Vec3 at = Fx.center(e);
		Fx.dust(level, at, boss.rank().rgb(), 1.8f, 10 + boss.rank().stars() * 8, 0.8);
		if (boss.rank() == BossRank.ASCENDANT) {
			Fx.particle(level, ParticleTypes.END_ROD, at, 120, 1.0, 0.3);
			Fx.particle(level, ParticleTypes.TOTEM_OF_UNDYING, at, 120, 1.0, 0.5);
			lightningEffect(level, e.position());
		} else if (boss.rank() == BossRank.GOLD) {
			Fx.particle(level, ParticleTypes.TOTEM_OF_UNDYING, at, 80, 0.8, 0.4);
		} else if (boss.rank() == BossRank.PURPLE) {
			Fx.particle(level, ParticleTypes.WITCH, at, 40, 0.8, 0.1);
		}
		Fx.play(level, e.position(), p.deathSound());

		if (p.deathBroadcast() == RankSettings.Broadcast.NONE || !bosses.settings().bossChat) {
			return;
		}
		Component msg = killer == null
			? messages().prefixed("slain-unknown", values("boss", boss.name()))
			: messages().prefixed("slain", values("boss", boss.name(), "player", killer.getDisplayName()));
		if (p.deathBroadcast() == RankSettings.Broadcast.SERVER) {
			level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
		} else {
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(e) <= 64 * 64) {
					player.sendSystemMessage(msg);
				}
			}
		}
	}

	/** A lightning flash and thunder that doesn't hurt anything or start fires. */
	public static void lightningEffect(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	/** Everyone nearby sees a promoted boss's new rank; Ascendant promotions go to the whole world. */
	public void announcePromotion(Boss boss, @Nullable ServerPlayer by) {
		LivingEntity e = boss.entity();
		Component title = rankTitle(boss.rank());
		for (ServerPlayer player : boss.level().players()) {
			if (player.distanceToSqr(e) > 64 * 64) {
				continue;
			}
			Fx.title(player, title, messages().get("promoted-subtitle", values("boss", boss.name())), 4, 44, 14);
			Fx.playTo(player, of(boss).sound());
		}
		if (boss.rank() == BossRank.ASCENDANT) {
			broadcast(boss.level(), messages().prefixed("ascended", values("boss", boss.name(),
				"player", by == null ? Component.literal("Someone") : by.getDisplayName())));
		}
	}

	/** A boss message for one player: in chat if boss chat is on, otherwise above their hotbar. */
	public void tell(ServerPlayer player, String key, Map<String, Component> values) {
		if (bosses.settings().bossChat) {
			player.sendSystemMessage(messages().prefixed(key, values));
		} else {
			Fx.actionBar(player, messages().get(key, values));
		}
	}

	public void tell(ServerPlayer player, String key) {
		tell(player, key, Map.of());
	}

	/** World-wide boss news. Only sent when boss chat is on. */
	public void broadcast(ServerLevel level, Component message) {
		if (bosses.settings().bossChat) {
			level.getServer().getPlayerList().broadcastSystemMessage(message, false);
		}
	}

	public void messageNearby(Boss boss, String key, double radius) {
		Component msg = messages().get(key, values("boss", boss.name()));
		for (ServerPlayer player : boss.level().players()) {
			if (player.distanceToSqr(boss.entity()) <= radius * radius) {
				Fx.actionBar(player, msg);
			}
		}
	}

	public static Map<String, Component> placeholders(Object... pairs) {
		return values(pairs);
	}
}
