package com.marionette.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A plain "are you sure" dialog with Yes/No buttons — used before anything
 * destructive (deleting a take or path) that can't be undone. Both buttons
 * return to the caller-supplied parent screen (usually "reopen the list,
 * refreshed") rather than falling through to whatever {@code onClose()}
 * would otherwise do.
 */
public class ConfirmScreen extends Screen {
	private final String message;
	private final Runnable onConfirm;
	private final Runnable returnToParent;

	public ConfirmScreen(String title, String message, Runnable onConfirm, Runnable returnToParent) {
		super(Component.literal(title));
		this.message = message;
		this.onConfirm = onConfirm;
		this.returnToParent = returnToParent;
	}

	@Override
	protected void init() {
		int y = this.height / 2 - 10;
		addRenderableWidget(disabled(message, y - 24));

		addRenderableWidget(Button.builder(Component.literal("Yes, delete it"), button -> {
			onConfirm.run();
			returnToParent.run();
		}).bounds(this.width / 2 - 105, y, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> returnToParent.run())
				.bounds(this.width / 2 + 5, y, 100, 20).build());
	}

	private Button disabled(String text, int y) {
		int width = Math.min(300, this.width - 40);
		Button button = Button.builder(Component.literal(text), b -> {})
				.bounds((this.width - width) / 2, y, width, 20).build();
		button.active = false;
		return button;
	}
}
