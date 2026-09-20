package com.marionette.client.gui;

import com.marionette.recording.RecordingStorage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A small "name this" dialog: one text field, one Save button. Used
 * whenever a take is finished recording or renamed, so it doesn't have to
 * live forever under an auto-generated timestamp name.
 *
 * <p>Doesn't call {@code onClose()} itself — {@code Screen.onClose()}
 * unconditionally sets the active screen to {@code null}, which would stomp
 * on whatever the confirm callback navigates to next (e.g. reopening a
 * refreshed list screen after a rename). The callback is responsible for
 * deciding what's shown afterward.
 */
public class NamePromptScreen extends Screen {
	private final String defaultName;
	private final Consumer<String> onConfirm;
	private EditBox nameBox;

	public NamePromptScreen(String title, String defaultName, Consumer<String> onConfirm) {
		super(Component.literal(title));
		this.defaultName = defaultName;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		int boxWidth = Math.min(240, this.width - 40);
		int x = (this.width - boxWidth) / 2;
		int y = this.height / 2 - 16;

		nameBox = new EditBox(font, x, y, boxWidth, 20, Component.literal("Name"));
		nameBox.setMaxLength(48);
		nameBox.setValue(defaultName);
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);

		addRenderableWidget(Button.builder(Component.literal("Save"), button -> confirm())
				.bounds(x, y + 28, boxWidth, 20).build());
	}

	private void confirm() {
		onConfirm.accept(RecordingStorage.sanitizeName(nameBox.getValue()));
	}
}
