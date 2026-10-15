package com.bloomcraft.core;

import com.bloomcraft.BloomCraft;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The mod's YAML files in config/bloomcraft/. Each starts as a copy of the default inside the jar (with comments),
 * and the player can edit it; a file that can't be read falls back to the default.
 */
public final class ConfigFiles {

	private ConfigFiles() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("bloomcraft");
	}

	/** Loads config/bloomcraft/{name}, creating it from the default first if it doesn't exist. */
	public static Cfg load(String name) {
		Path file = dir().resolve(name);
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				try (InputStream in = defaults(name)) {
					if (in != null) {
						Files.copy(in, file);
					}
				}
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				return new Cfg(parse(reader));
			}
		} catch (IOException | RuntimeException ex) {
			BloomCraft.LOG.error("Couldn't read {} ({}); using the built-in defaults", file, ex.toString());
			return loadDefault(name);
		}
	}

	/** The default file inside the jar. */
	public static Cfg loadDefault(String name) {
		try (InputStream in = defaults(name)) {
			if (in == null) {
				return Cfg.empty();
			}
			return new Cfg(parse(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)));
		} catch (IOException | RuntimeException ex) {
			BloomCraft.LOG.error("Couldn't read the default {}: {}", name, ex.toString());
			return Cfg.empty();
		}
	}

	private static InputStream defaults(String name) {
		return ConfigFiles.class.getResourceAsStream("/bloomcraft/" + name);
	}

	private static Map<?, ?> parse(Reader reader) {
		Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
		Object root = yaml.load(reader);
		return root instanceof Map<?, ?> m ? m : Map.of();
	}
}
