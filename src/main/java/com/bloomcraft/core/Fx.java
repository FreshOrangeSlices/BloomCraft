package com.bloomcraft.core;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Sounds, particles, titles and the action bar. */
public final class Fx {

	private Fx() {
	}

	/** A sound with its volume and pitch, parsed from config text like "entity.wither.spawn 0.6 1.1". */
	public record Sound(Holder<SoundEvent> event, float volume, float pitch) {
	}

	public static Sound sound(String key, float volume, float pitch) {
		Identifier id = Identifier.parse(key.toLowerCase(Locale.ROOT));
		return new Sound(Holder.direct(SoundEvent.createVariableRangeEvent(id)), volume, pitch);
	}

	/** Parses "entity.wither.spawn", "entity.wither.spawn 0.6" or "entity.wither.spawn 0.6 1.2" (null if blank). */
	public static @Nullable Sound parseSound(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String[] parts = raw.trim().split("\\s+");
		try {
			float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
			float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
			return sound(parts[0], volume, pitch);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	/** Everyone nearby hears it. */
	public static void play(ServerLevel level, Vec3 at, @Nullable Sound sound) {
		if (sound != null) {
			level.playSound(null, at.x, at.y, at.z, sound.event(), SoundSource.HOSTILE, sound.volume(), sound.pitch());
		}
	}

	public static void play(ServerLevel level, Vec3 at, String key, float volume, float pitch) {
		play(level, at, sound(key, volume, pitch));
	}

	public static void play(Entity at, String key, float volume, float pitch) {
		if (at.level() instanceof ServerLevel level) {
			play(level, at.position(), sound(key, volume, pitch));
		}
	}

	/** A friendly sound (pets, menus) everyone nearby hears. */
	public static void neutral(Entity at, String key, float volume, float pitch) {
		if (at.level() instanceof ServerLevel level) {
			Sound sound = sound(key, volume, pitch);
			level.playSound(null, at.getX(), at.getY(), at.getZ(), sound.event(), SoundSource.NEUTRAL, volume, pitch);
		}
	}

	/** Only this player hears it. */
	public static void playTo(ServerPlayer player, @Nullable Sound sound) {
		if (sound != null) {
			player.connection.send(new ClientboundSoundPacket(sound.event(), SoundSource.HOSTILE, player.getX(),
				player.getY(), player.getZ(), sound.volume(), sound.pitch(), player.getRandom().nextLong()));
		}
	}

	public static void playTo(ServerPlayer player, String key, float volume, float pitch) {
		playTo(player, sound(key, volume, pitch));
	}

	/** Only this player hears it, coming from {@code at} (sounds "behind you"). */
	public static void playToAt(ServerPlayer player, String key, Vec3 at, float volume, float pitch) {
		Sound sound = sound(key, volume, pitch);
		player.connection.send(new ClientboundSoundPacket(sound.event(), SoundSource.HOSTILE, at.x, at.y, at.z,
			sound.volume(), sound.pitch(), player.getRandom().nextLong()));
	}

	/** Only this player sees them. */
	public static void particleTo(ServerPlayer player, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		if (count > 0) {
			player.level().sendParticles(player, particle, false, false, at.x, at.y, at.z, count, spread, spread, spread, speed);
		}
	}

	public static void particle(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		if (count > 0) {
			level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
		}
	}

	public static void particle(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double dx, double dy,
								double dz, double speed) {
		if (count > 0) {
			level.sendParticles(particle, at.x, at.y, at.z, count, dx, dy, dz, speed);
		}
	}

	public static void dust(ServerLevel level, Vec3 at, int rgb, float size, int count, double spread) {
		particle(level, new DustParticleOptions(rgb, size), at, count, spread, 0);
	}

	public static Vec3 center(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() / 2.0, 0);
	}

	// ---------------- titles + action bar ----------------

	public static void actionBar(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message);
	}

	public static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}
}
