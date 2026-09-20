package com.marionette.keyframe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Persists keyframe sequences as JSON under config/marionette/keyframes,
 * separate from the macro takes directory since these are a different kind
 * of recording.
 */
public final class KeyframeStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger("marionette");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private KeyframeStorage() {
	}

	private static Path directory() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("marionette").resolve("keyframes");
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			LOGGER.error("Could not create keyframes directory", e);
		}
		return path;
	}

	public static void save(KeyframeSequence sequence) {
		Path file = directory().resolve(com.marionette.recording.RecordingStorage.sanitizeName(sequence.name()) + ".json");
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(sequence, writer);
		} catch (IOException e) {
			LOGGER.error("Failed to save keyframe sequence {}", sequence.name(), e);
		}
	}

	/** Names of every saved path, newest first. */
	public static List<String> listNames() {
		List<Path> files;
		try (var stream = Files.list(directory())) {
			files = stream.filter(p -> p.toString().endsWith(".json")).toList();
		} catch (IOException e) {
			LOGGER.error("Failed to list keyframe sequences", e);
			return List.of();
		}
		List<Path> sorted = new ArrayList<>(files);
		sorted.sort(Comparator.comparingLong(KeyframeStorage::lastModified).reversed());
		List<String> names = new ArrayList<>(sorted.size());
		for (Path path : sorted) {
			String fileName = path.getFileName().toString();
			names.add(fileName.substring(0, fileName.length() - ".json".length()));
		}
		return names;
	}

	public static KeyframeSequence load(String name) {
		Path file = directory().resolve(name + ".json");
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, KeyframeSequence.class);
		} catch (IOException e) {
			LOGGER.error("Failed to load keyframe sequence {}", name, e);
			return null;
		}
	}

	public static void delete(String name) {
		Path file = directory().resolve(name + ".json");
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			LOGGER.error("Failed to delete keyframe sequence {}", name, e);
		}
	}

	public static KeyframeSequence loadLatest() {
		Optional<Path> latest;
		try (var files = Files.list(directory())) {
			latest = files
					.filter(p -> p.toString().endsWith(".json"))
					.max(Comparator.comparingLong(KeyframeStorage::lastModified));
		} catch (IOException e) {
			LOGGER.error("Failed to list keyframe sequences", e);
			return null;
		}
		if (latest.isEmpty()) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(latest.get(), StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, KeyframeSequence.class);
		} catch (IOException e) {
			LOGGER.error("Failed to load keyframe sequence {}", latest.get(), e);
			return null;
		}
	}

	private static long lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
