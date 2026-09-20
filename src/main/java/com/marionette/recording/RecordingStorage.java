package com.marionette.recording;

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
 * Persists takes as JSON under config/marionette/recordings.
 */
public final class RecordingStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger("marionette");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private RecordingStorage() {
	}

	private static Path directory() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("marionette").resolve("recordings");
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			LOGGER.error("Could not create recordings directory", e);
		}
		return path;
	}

	public static void save(Recording recording) {
		Path file = directory().resolve(sanitizeName(recording.name()) + ".json");
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(recording, writer);
		} catch (IOException e) {
			LOGGER.error("Failed to save recording {}", recording.name(), e);
		}
	}

	/**
	 * Take names can now come straight from a player-typed text field, so
	 * this strips anything that isn't safe as a filename (path separators,
	 * leading dots, control characters) instead of writing it through raw.
	 */
	public static String sanitizeName(String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		String cleaned = trimmed.replaceAll("[^A-Za-z0-9 _.-]", "_");
		while (cleaned.startsWith(".")) {
			cleaned = cleaned.substring(1);
		}
		if (cleaned.length() > 64) {
			cleaned = cleaned.substring(0, 64);
		}
		if (cleaned.isBlank()) {
			cleaned = "take-" + System.currentTimeMillis();
		}
		return cleaned;
	}

	public static Recording loadLatest() {
		Optional<Path> latest;
		try (var files = Files.list(directory())) {
			latest = files
					.filter(p -> p.toString().endsWith(".json"))
					.max(Comparator.comparingLong(RecordingStorage::lastModified));
		} catch (IOException e) {
			LOGGER.error("Failed to list recordings", e);
			return null;
		}
		if (latest.isEmpty()) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(latest.get(), StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, Recording.class);
		} catch (IOException e) {
			LOGGER.error("Failed to load recording {}", latest.get(), e);
			return null;
		}
	}

	/** Names of every saved take, newest first. */
	public static List<String> listNames() {
		List<Path> files;
		try (var stream = Files.list(directory())) {
			files = stream.filter(p -> p.toString().endsWith(".json")).toList();
		} catch (IOException e) {
			LOGGER.error("Failed to list recordings", e);
			return List.of();
		}
		List<Path> sorted = new ArrayList<>(files);
		sorted.sort(Comparator.comparingLong(RecordingStorage::lastModified).reversed());
		List<String> names = new ArrayList<>(sorted.size());
		for (Path path : sorted) {
			String fileName = path.getFileName().toString();
			names.add(fileName.substring(0, fileName.length() - ".json".length()));
		}
		return names;
	}

	public static Recording load(String name) {
		Path file = directory().resolve(name + ".json");
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, Recording.class);
		} catch (IOException e) {
			LOGGER.error("Failed to load recording {}", name, e);
			return null;
		}
	}

	public static void delete(String name) {
		Path file = directory().resolve(name + ".json");
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			LOGGER.error("Failed to delete recording {}", name, e);
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
