package com.marionette.client.gui;

import com.marionette.keyframe.KeyframeSequence;
import com.marionette.keyframe.KeyframeStorage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Lists saved keyframe (cinematic) paths so one can be played, renamed, or
 * discarded — the keyframe-path counterpart to {@link TakesScreen}. There's
 * no visual path editor yet, so no Edit button here.
 */
public class KeyframesScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PLAY_WIDTH = 190;
	private static final int RENAME_WIDTH = 55;
	private static final int DELETE_WIDTH = 20;
	private static final int GAP = 4;

	private final BiConsumer<KeyframeSequence, Boolean> onPlay;
	private boolean loop;

	public KeyframesScreen(BiConsumer<KeyframeSequence, Boolean> onPlay) {
		super(Component.literal("Marionette Keyframe Paths"));
		this.onPlay = onPlay;
	}

	@Override
	protected void init() {
		List<String> names = KeyframeStorage.listNames();
		int totalWidth = PLAY_WIDTH + GAP + RENAME_WIDTH + GAP + DELETE_WIDTH;
		int x = (this.width - totalWidth) / 2;
		int y = 32;

		addRenderableWidget(Button.builder(loopLabel(), button -> {
			loop = !loop;
			button.setMessage(loopLabel());
		}).bounds(x, 8, totalWidth, BUTTON_HEIGHT).build());

		if (names.isEmpty()) {
			addRenderableWidget(disabledLabel("No paths recorded yet — press K in-game to start one", x, y, totalWidth));
		}

		for (String name : names) {
			KeyframeSequence sequence = KeyframeStorage.load(name);
			int count = sequence != null ? sequence.keyframes().size() : 0;
			Component label = Component.literal(name + " (" + count + " keyframes)");
			int rowX = x;

			addRenderableWidget(Button.builder(label, button -> {
				if (sequence != null) {
					onPlay.accept(sequence, loop);
				}
				onClose();
			}).bounds(rowX, y, PLAY_WIDTH, BUTTON_HEIGHT).build());
			rowX += PLAY_WIDTH + GAP;

			addRenderableWidget(Button.builder(Component.literal("Rename"), button -> renamePrompt(name, sequence))
					.bounds(rowX, y, RENAME_WIDTH, BUTTON_HEIGHT).build());
			rowX += RENAME_WIDTH + GAP;

			addRenderableWidget(Button.builder(Component.literal("X"), button -> minecraft.setScreenAndShow(new ConfirmScreen(
					"Delete path?",
					"Delete '" + name + "'? This can't be undone.",
					() -> KeyframeStorage.delete(name),
					() -> minecraft.setScreenAndShow(new KeyframesScreen(onPlay))
			))).bounds(rowX, y, DELETE_WIDTH, BUTTON_HEIGHT).build());

			y += ROW_HEIGHT;
		}

		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
				.bounds((this.width - 100) / 2, this.height - 32, 100, BUTTON_HEIGHT)
				.build());
	}

	private void renamePrompt(String currentName, KeyframeSequence sequence) {
		if (sequence == null) {
			return;
		}
		minecraft.setScreenAndShow(new NamePromptScreen("Rename path", currentName, newName -> {
			KeyframeStorage.rename(currentName, newName);
			minecraft.setScreenAndShow(new KeyframesScreen(onPlay));
		}));
	}

	private Component loopLabel() {
		return Component.literal(loop ? "Loop: ON (plays until stopped with I)" : "Loop: OFF (plays once)");
	}

	private Button disabledLabel(String text, int x, int y, int width) {
		Button button = Button.builder(Component.literal(text), b -> {}).bounds(x, y, width, BUTTON_HEIGHT).build();
		button.active = false;
		return button;
	}
}
