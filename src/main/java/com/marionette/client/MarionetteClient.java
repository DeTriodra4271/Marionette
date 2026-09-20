package com.marionette.client;

import com.marionette.client.gui.KeyframesScreen;
import com.marionette.client.gui.NamePromptScreen;
import com.marionette.client.gui.TakesScreen;
import com.marionette.keyframe.KeyframePlayer;
import com.marionette.keyframe.KeyframeRecorder;
import com.marionette.keyframe.KeyframeSequence;
import com.marionette.keyframe.KeyframeStorage;
import com.marionette.recording.Player;
import com.marionette.recording.Recorder;
import com.marionette.recording.Recording;
import com.marionette.recording.RecordingStorage;
import com.marionette.timeline.TimelineDecompiler;
import com.marionette.timeline.TimelineScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarionetteClient implements ClientModInitializer {
	public static final String MOD_ID = "marionette";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private final Recorder recorder = new Recorder();
	private final Player player = new Player();
	private Recording lastRecording;

	private final KeyframeRecorder keyframeRecorder = new KeyframeRecorder();
	private final KeyframePlayer keyframePlayer = new KeyframePlayer();
	private KeyframeSequence lastKeyframeSequence;

	private KeyMapping recordKey;
	private KeyMapping playKey;
	private KeyMapping menuKey;
	private KeyMapping keyframeAddKey;
	private KeyMapping keyframeFinishKey;
	private KeyMapping keyframePlayKey;
	private KeyMapping timelineKey;
	private KeyMapping keyframeMenuKey;

	@Override
	public void onInitializeClient() {
		recordKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.record",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				CATEGORY
		));
		playKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.play",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				CATEGORY
		));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				CATEGORY
		));
		keyframeAddKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.keyframe_add",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				CATEGORY
		));
		keyframeFinishKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.keyframe_finish",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_L,
				CATEGORY
		));
		keyframePlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.keyframe_play",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_I,
				CATEGORY
		));
		timelineKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.timeline",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				CATEGORY
		));
		keyframeMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.marionette.keyframe_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_U,
				CATEGORY
		));

		ClientTickEvents.START_CLIENT_TICK.register(player::applyInput);
		ClientTickEvents.START_CLIENT_TICK.register(recorder::tickStart);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (recordKey.consumeClick()) {
				toggleRecord(client);
			}
			while (playKey.consumeClick()) {
				togglePlay(client);
			}
			while (menuKey.consumeClick()) {
				openMenu(client);
			}
			while (keyframeAddKey.consumeClick()) {
				addKeyframe(client);
			}
			while (keyframeFinishKey.consumeClick()) {
				finishKeyframes(client);
			}
			while (keyframePlayKey.consumeClick()) {
				toggleKeyframePlayback(client);
			}
			while (timelineKey.consumeClick()) {
				openTimeline(client);
			}
			while (keyframeMenuKey.consumeClick()) {
				openKeyframesMenu(client);
			}
			player.applyInteractions(client);
			recorder.tick(client);
			keyframeRecorder.tick();
			keyframePlayer.tick(client);
		});
	}

	private void toggleRecord(Minecraft client) {
		if (recorder.isRecording()) {
			Recording recorded = recorder.stop("take-" + System.currentTimeMillis());
			int frameCount = recorded.frames().size();
			client.setScreenAndShow(new NamePromptScreen("Name this take", recorded.name(), chosenName -> {
				Recording named = new Recording(chosenName, recorded.frames(),
						recorded.initialVelocityX(), recorded.initialVelocityY(), recorded.initialVelocityZ());
				RecordingStorage.save(named);
				lastRecording = named;
				Minecraft mc = Minecraft.getInstance();
				message(mc, "Marionette: saved take '" + chosenName + "' (" + frameCount + " ticks)");
				mc.setScreenAndShow(null);
			}));
		} else {
			if (client.player == null) {
				return;
			}
			if (player.isPlaying()) {
				player.stop();
			}
			recorder.start(client.player);
			message(client, "Marionette: recording...");
		}
	}

	private void togglePlay(Minecraft client) {
		if (player.isPlaying()) {
			player.stop();
			message(client, "Marionette: playback stopped");
			return;
		}
		if (lastRecording == null) {
			lastRecording = RecordingStorage.loadLatest();
		}
		if (lastRecording == null) {
			message(client, "Marionette: no take available yet");
			return;
		}
		playRecording(lastRecording, false);
	}

	private void openMenu(Minecraft client) {
		if (client.player == null) {
			return;
		}
		client.setScreenAndShow(new TakesScreen(this::playRecording, this::editRecording));
	}

	private void openTimeline(Minecraft client) {
		if (client.player == null) {
			return;
		}
		client.setScreenAndShow(new TimelineScreen(recording -> playRecording(recording, false)));
	}

	private void editRecording(Recording recording) {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		TimelineDecompiler.Decompiled decompiled = TimelineDecompiler.decompile(recording);
		Minecraft.getInstance().setScreenAndShow(new TimelineScreen(
				edited -> playRecording(edited, false),
				recording.name(),
				decompiled.rotations(),
				decompiled.actions()
		));
	}

	private void playRecording(Recording recording, boolean loop) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (recorder.isRecording()) {
			return;
		}
		if (keyframePlayer.isPlaying()) {
			keyframePlayer.stop();
		}
		lastRecording = recording;
		if (player.isPlaying()) {
			player.stop();
		}
		player.start(recording, client.player, loop);
		message(client, "Marionette: playing back (" + recording.frames().size() + " ticks)" + (loop ? " [looping]" : ""));
	}

	private void addKeyframe(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (player.isPlaying()) {
			player.stop();
		}
		if (keyframePlayer.isPlaying()) {
			keyframePlayer.stop();
		}
		int count = keyframeRecorder.addKeyframe(client.player);
		message(client, "Marionette: keyframe " + count + " added");
	}

	private void finishKeyframes(Minecraft client) {
		if (!keyframeRecorder.isRecording()) {
			message(client, "Marionette: no keyframe take in progress");
			return;
		}
		KeyframeSequence recorded = keyframeRecorder.finish("keyframes-" + System.currentTimeMillis());
		int count = recorded.keyframes().size();
		client.setScreenAndShow(new NamePromptScreen("Name this path", recorded.name(), chosenName -> {
			KeyframeSequence named = new KeyframeSequence(chosenName, recorded.keyframes());
			KeyframeStorage.save(named);
			lastKeyframeSequence = named;
			Minecraft mc = Minecraft.getInstance();
			message(mc, "Marionette: saved path '" + chosenName + "' (" + count + " keyframes)");
			mc.setScreenAndShow(null);
		}));
	}

	private void toggleKeyframePlayback(Minecraft client) {
		if (keyframePlayer.isPlaying()) {
			keyframePlayer.stop();
			message(client, "Marionette: path playback stopped");
			return;
		}
		if (lastKeyframeSequence == null) {
			lastKeyframeSequence = KeyframeStorage.loadLatest();
		}
		if (lastKeyframeSequence == null) {
			message(client, "Marionette: no keyframe path available yet");
			return;
		}
		playKeyframeSequence(lastKeyframeSequence, false);
	}

	private void openKeyframesMenu(Minecraft client) {
		if (client.player == null) {
			return;
		}
		client.setScreenAndShow(new KeyframesScreen(this::playKeyframeSequence));
	}

	private void playKeyframeSequence(KeyframeSequence sequence, boolean loop) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (player.isPlaying()) {
			player.stop();
		}
		lastKeyframeSequence = sequence;
		keyframePlayer.start(sequence, loop);
		message(client, "Marionette: flying path (" + sequence.keyframes().size() + " keyframes)" + (loop ? " [looping]" : ""));
	}

	private void message(Minecraft client, String text) {
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal(text));
		}
	}
}
