package com.marionette.client.gui;

import com.marionette.recording.LoopMode;
import com.marionette.recording.Recording;
import com.marionette.recording.RecordingStorage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lists saved takes so one can be picked for playback, edited in the
 * timeline editor, renamed, or discarded.
 */
public class TakesScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PLAY_WIDTH = 150;
	private static final int EDIT_WIDTH = 40;
	private static final int RENAME_WIDTH = 55;
	private static final int DELETE_WIDTH = 20;
	private static final int GAP = 4;

	private final BiConsumer<Recording, LoopMode> onPlay;
	private final Consumer<Recording> onEdit;
	private LoopMode loop = LoopMode.OFF;

	public TakesScreen(BiConsumer<Recording, LoopMode> onPlay, Consumer<Recording> onEdit) {
		super(Component.literal("Marionette Takes"));
		this.onPlay = onPlay;
		this.onEdit = onEdit;
	}

	@Override
	protected void init() {
		List<String> names = RecordingStorage.listNames();
		int totalWidth = PLAY_WIDTH + GAP + EDIT_WIDTH + GAP + RENAME_WIDTH + GAP + DELETE_WIDTH;
		int x = (this.width - totalWidth) / 2;
		int y = 32;

		addRenderableWidget(Button.builder(loopLabel(), button -> {
			loop = LoopMode.values()[(loop.ordinal() + 1) % LoopMode.values().length];
			button.setMessage(loopLabel());
		}).bounds(x, 8, totalWidth, BUTTON_HEIGHT).build());

		if (names.isEmpty()) {
			addRenderableWidget(disabledLabel("No takes recorded yet — press R in-game to start one", x, y, totalWidth));
		}

		for (String name : names) {
			Recording recording = RecordingStorage.load(name);
			int frameCount = recording != null ? recording.frames().size() : 0;
			Component label = Component.literal(name + " (" + frameCount + " ticks)");
			int rowX = x;

			addRenderableWidget(Button.builder(label, button -> {
				if (recording != null) {
					onPlay.accept(recording, loop);
				}
				onClose();
			}).bounds(rowX, y, PLAY_WIDTH, BUTTON_HEIGHT).build());
			rowX += PLAY_WIDTH + GAP;

			addRenderableWidget(Button.builder(Component.literal("Edit"), button -> {
				if (recording != null) {
					onEdit.accept(recording);
				}
			}).bounds(rowX, y, EDIT_WIDTH, BUTTON_HEIGHT).build());
			rowX += EDIT_WIDTH + GAP;

			addRenderableWidget(Button.builder(Component.literal("Rename"), button -> renamePrompt(name, recording))
					.bounds(rowX, y, RENAME_WIDTH, BUTTON_HEIGHT).build());
			rowX += RENAME_WIDTH + GAP;

			addRenderableWidget(Button.builder(Component.literal("X"), button -> minecraft.setScreenAndShow(new ConfirmScreen(
					"Delete take?",
					"Delete '" + name + "'? This can't be undone.",
					() -> RecordingStorage.delete(name),
					() -> minecraft.setScreenAndShow(new TakesScreen(onPlay, onEdit))
			))).bounds(rowX, y, DELETE_WIDTH, BUTTON_HEIGHT).build());

			y += ROW_HEIGHT;
		}

		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
				.bounds((this.width - 100) / 2, this.height - 32, 100, BUTTON_HEIGHT)
				.build());
	}

	/** Renames without touching the frame data at all — unlike Edit, this never round-trips through the (lossy) timeline decompiler. */
	private void renamePrompt(String currentName, Recording recording) {
		if (recording == null) {
			return;
		}
		minecraft.setScreenAndShow(new NamePromptScreen("Rename take", currentName, newName -> {
			RecordingStorage.rename(currentName, newName);
			minecraft.setScreenAndShow(new TakesScreen(onPlay, onEdit));
		}));
	}

	private Component loopLabel() {
		return Component.literal(switch (loop) {
			case OFF -> "Loop: OFF (plays once)";
			case REPEAT -> "Loop: ON (repeats from where it ends)";
			case RETURN_TO_START -> "Loop: ON, back to the start each time";
		});
	}

	private Button disabledLabel(String text, int x, int y, int width) {
		Button button = Button.builder(Component.literal(text), b -> {}).bounds(x, y, width, BUTTON_HEIGHT).build();
		button.active = false;
		return button;
	}
}
