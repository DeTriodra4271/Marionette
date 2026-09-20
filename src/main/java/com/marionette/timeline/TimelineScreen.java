package com.marionette.timeline;

import com.marionette.recording.Recording;
import com.marionette.recording.RecordingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A Blender-dopesheet-style editor: action blocks (move/jump/attack/use/
 * sneak/sprint) and look-direction keyframes laid out on a shared time
 * ruler, one row per action type.
 *
 * <p>Blocks are drawn directly (not as widgets) and are draggable like a
 * video editor's clips: grab the middle to slide a block in time, grab
 * either edge to trim its length, drag a look keyframe to retime it.
 * Right-click removes one. The numeric form at the bottom is still there
 * for adding new ones precisely.
 *
 * <p>"Play" and "Save" both compile the timeline into a plain
 * {@link Recording} via {@link TimelineCompiler} and hand it to the same
 * playback path as a hand-recorded take, so timing, relative positioning,
 * and physics all behave exactly like an ordinary macro.
 */
public class TimelineScreen extends Screen {
	private static final int PIXELS_PER_SECOND = 24;
	private static final int ROW_HEIGHT = 18;
	private static final int TIMELINE_X = 90;
	private static final int RULER_Y = 28;
	private static final int ROWS_Y = 46;
	private static final int EDGE_GRAB_PX = 4;
	private static final float MIN_DURATION = 0.05f;
	private static final float TICK_SECONDS = 0.05f;

	private static final int COLOR_ACTION = 0xFF4A90D9;
	private static final int COLOR_ACTION_DRAGGING = 0xFF7FB4F0;
	private static final int COLOR_ROTATION = 0xFF2ECC71;
	private static final int COLOR_ROTATION_DRAGGING = 0xFF6FF2A0;
	private static final int COLOR_OUTLINE = 0xFF000000;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_RULER_LINE = 0xFF555555;

	private enum DragMode {MOVE, RESIZE_START, RESIZE_END}

	private final Consumer<Recording> onPlay;
	private final List<RotationKeyframe> rotations = new ArrayList<>();
	private final List<ActionInterval> actions = new ArrayList<>();
	private final String initialName;

	private ActionType pendingType = ActionType.FORWARD;
	private EditBox startBox;
	private EditBox endBox;
	private EditBox rotationTimeBox;
	private EditBox nameBox;

	private int draggingActionIndex = -1;
	private DragMode dragMode;
	private float dragAnchorOffset;
	private int draggingRotationIndex = -1;

	public TimelineScreen(Consumer<Recording> onPlay) {
		this(onPlay, null, List.of(), List.of());
	}

	/** Opens pre-populated with an existing take's decompiled timeline, so it can be edited and re-saved under the same name. */
	public TimelineScreen(Consumer<Recording> onPlay, String existingName, List<RotationKeyframe> initialRotations, List<ActionInterval> initialActions) {
		super(Component.literal("Marionette Timeline"));
		this.onPlay = onPlay;
		this.initialName = existingName;
		this.rotations.addAll(initialRotations);
		this.actions.addAll(initialActions);
	}

	@Override
	protected void init() {
		if (rotations.isEmpty()) {
			LocalPlayer player = minecraft.player;
			if (player != null) {
				rotations.add(new RotationKeyframe(0f, player.getYRot(), player.getXRot()));
			}
		}

		int types = ActionType.values().length;
		int rotationRowY = ROWS_Y + types * ROW_HEIGHT;
		int formY = rotationRowY + ROW_HEIGHT + 14;

		addRenderableWidget(disabled("Add action:", 6, formY, 80));
		addRenderableWidget(CycleButton.builder((ActionType t) -> Component.literal(label(t)), pendingType)
				.withValues(ActionType.values())
				.create(90, formY, 90, ROW_HEIGHT, Component.literal("Action"), (button, value) -> pendingType = value));

		startBox = new EditBox(font, 190, formY, 45, ROW_HEIGHT, Component.literal("Start"));
		startBox.setValue("0");
		startBox.setMaxLength(6);
		addRenderableWidget(startBox);

		endBox = new EditBox(font, 240, formY, 45, ROW_HEIGHT, Component.literal("End"));
		endBox.setValue("0");
		endBox.setMaxLength(6);
		addRenderableWidget(endBox);

		addRenderableWidget(Button.builder(Component.literal("+ Add"), button -> {
			float start = parse(startBox.getValue(), 0f);
			float end = parse(endBox.getValue(), start);
			actions.add(new ActionInterval(pendingType, Math.max(0f, start), Math.max(start, end)));
		}).bounds(290, formY, 60, ROW_HEIGHT).build());

		int rotFormY = formY + ROW_HEIGHT + 6;
		addRenderableWidget(disabled("Add look keyframe at:", 6, rotFormY, 130));
		rotationTimeBox = new EditBox(font, 140, rotFormY, 45, ROW_HEIGHT, Component.literal("Time"));
		rotationTimeBox.setValue("0");
		rotationTimeBox.setMaxLength(6);
		addRenderableWidget(rotationTimeBox);

		addRenderableWidget(Button.builder(Component.literal("Capture facing here"), button -> {
			LocalPlayer player = minecraft.player;
			if (player == null) {
				return;
			}
			float time = Math.max(0f, parse(rotationTimeBox.getValue(), 0f));
			rotations.removeIf(r -> r.time() == time);
			rotations.add(new RotationKeyframe(time, player.getYRot(), player.getXRot()));
		}).bounds(190, rotFormY, 160, ROW_HEIGHT).build());

		int nameFormY = rotFormY + ROW_HEIGHT + 6;
		addRenderableWidget(disabled("Name:", 6, nameFormY, 50));
		nameBox = new EditBox(font, 60, nameFormY, 220, ROW_HEIGHT, Component.literal("Name"));
		nameBox.setMaxLength(48);
		nameBox.setValue(initialName != null ? initialName : ("timeline-" + System.currentTimeMillis()));
		addRenderableWidget(nameBox);

		int bottomY = this.height - 28;
		addRenderableWidget(Button.builder(Component.literal("Play"), button -> {
			onPlay.accept(TimelineCompiler.compile(currentName(), rotations, actions));
			onClose();
		}).bounds(this.width / 2 - 130, bottomY, 80, ROW_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
			Recording compiled = TimelineCompiler.compile(currentName(), rotations, actions);
			RecordingStorage.save(compiled);
		}).bounds(this.width / 2 - 40, bottomY, 80, ROW_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
				.bounds(this.width / 2 + 50, bottomY, 80, ROW_HEIGHT).build());
	}

	private String currentName() {
		return RecordingStorage.sanitizeName(nameBox.getValue());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
		drawTimeline(guiGraphics);
	}

	private void drawTimeline(GuiGraphicsExtractor g) {
		float viewSeconds = Math.max(15f, maxTime() + 3f);
		int rulerWidth = timeToX(viewSeconds) - TIMELINE_X;

		g.horizontalLine(TIMELINE_X, TIMELINE_X + rulerWidth, RULER_Y + 9, COLOR_RULER_LINE);
		for (int second = 0; second <= (int) viewSeconds; second += 5) {
			int x = timeToX(second);
			g.text(font, second + "s", x, RULER_Y, COLOR_TEXT);
			g.verticalLine(x, RULER_Y + 9, RULER_Y + 12, COLOR_RULER_LINE);
		}

		ActionType[] types = ActionType.values();
		for (int row = 0; row < types.length; row++) {
			ActionType type = types[row];
			int y = rowY(row);
			g.text(font, label(type), 6, y + 5, COLOR_TEXT);
			g.horizontalLine(TIMELINE_X, TIMELINE_X + rulerWidth, y, COLOR_RULER_LINE);

			for (int i = 0; i < actions.size(); i++) {
				ActionInterval action = actions.get(i);
				if (action.type() != type) {
					continue;
				}
				int x1 = timeToX(action.start());
				int x2 = Math.max(x1 + 6, timeToX(action.end()));
				boolean dragging = i == draggingActionIndex;
				g.fill(x1, y, x2, y + ROW_HEIGHT - 2, dragging ? COLOR_ACTION_DRAGGING : COLOR_ACTION);
				g.outline(x1, y, x2 - x1, ROW_HEIGHT - 2, COLOR_OUTLINE);
				String text = formatSeconds(action.start()) + (action.end() > action.start() ? "-" + formatSeconds(action.end()) : "");
				g.text(font, text, x1 + 3, y + 4, COLOR_TEXT);
			}
		}

		int rotationRowY = rowY(types.length);
		g.text(font, "Look", 6, rotationRowY + 5, COLOR_TEXT);
		g.horizontalLine(TIMELINE_X, TIMELINE_X + rulerWidth, rotationRowY, COLOR_RULER_LINE);
		for (int i = 0; i < rotations.size(); i++) {
			RotationKeyframe keyframe = rotations.get(i);
			int cx = timeToX(keyframe.time());
			boolean dragging = i == draggingRotationIndex;
			g.fill(cx - 5, rotationRowY, cx + 5, rotationRowY + ROW_HEIGHT - 2, dragging ? COLOR_ROTATION_DRAGGING : COLOR_ROTATION);
			g.outline(cx - 5, rotationRowY, 10, ROW_HEIGHT - 2, COLOR_OUTLINE);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int actionIndex = findActionAt(event.x(), event.y());
			if (actionIndex >= 0) {
				ActionInterval action = actions.get(actionIndex);
				int x1 = timeToX(action.start());
				int x2 = Math.max(x1 + 6, timeToX(action.end()));
				if (Math.abs(event.x() - x2) <= EDGE_GRAB_PX) {
					dragMode = DragMode.RESIZE_END;
				} else if (Math.abs(event.x() - x1) <= EDGE_GRAB_PX) {
					dragMode = DragMode.RESIZE_START;
				} else {
					dragMode = DragMode.MOVE;
					dragAnchorOffset = xToTime(event.x()) - action.start();
				}
				draggingActionIndex = actionIndex;
				return true;
			}
			int rotationIndex = findRotationAt(event.x(), event.y());
			if (rotationIndex >= 0) {
				draggingRotationIndex = rotationIndex;
				return true;
			}
		} else if (event.button() == 1) {
			int actionIndex = findActionAt(event.x(), event.y());
			if (actionIndex >= 0) {
				actions.remove(actionIndex);
				return true;
			}
			int rotationIndex = findRotationAt(event.x(), event.y());
			if (rotationIndex >= 0) {
				rotations.remove(rotationIndex);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingActionIndex >= 0 && draggingActionIndex < actions.size()) {
			ActionInterval old = actions.get(draggingActionIndex);
			float t = xToTimeSnapped(event.x());
			float newStart = old.start();
			float newEnd = old.end();
			switch (dragMode) {
				case MOVE -> {
					float duration = old.end() - old.start();
					newStart = Math.max(0f, t - dragAnchorOffset);
					newEnd = newStart + duration;
				}
				case RESIZE_START -> newStart = Math.max(0f, Math.min(t, old.end() - MIN_DURATION));
				case RESIZE_END -> newEnd = Math.max(t, old.start() + MIN_DURATION);
			}
			actions.set(draggingActionIndex, new ActionInterval(old.type(), newStart, newEnd));
			return true;
		}
		if (draggingRotationIndex >= 0 && draggingRotationIndex < rotations.size()) {
			RotationKeyframe old = rotations.get(draggingRotationIndex);
			float t = Math.max(0f, xToTimeSnapped(event.x()));
			rotations.set(draggingRotationIndex, new RotationKeyframe(t, old.yaw(), old.pitch()));
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingActionIndex = -1;
		draggingRotationIndex = -1;
		return super.mouseReleased(event);
	}

	private int findActionAt(double mouseX, double mouseY) {
		ActionType[] types = ActionType.values();
		for (int i = actions.size() - 1; i >= 0; i--) {
			ActionInterval action = actions.get(i);
			int row = action.type().ordinal();
			int y = rowY(row);
			if (mouseY < y - 1 || mouseY > y + ROW_HEIGHT - 1) {
				continue;
			}
			int x1 = timeToX(action.start());
			int x2 = Math.max(x1 + 6, timeToX(action.end()));
			if (mouseX >= x1 - EDGE_GRAB_PX && mouseX <= x2 + EDGE_GRAB_PX) {
				return i;
			}
		}
		return -1;
	}

	private int findRotationAt(double mouseX, double mouseY) {
		int rotationRowY = rowY(ActionType.values().length);
		if (mouseY < rotationRowY - 1 || mouseY > rotationRowY + ROW_HEIGHT - 1) {
			return -1;
		}
		for (int i = rotations.size() - 1; i >= 0; i--) {
			int cx = timeToX(rotations.get(i).time());
			if (Math.abs(mouseX - cx) <= 6) {
				return i;
			}
		}
		return -1;
	}

	private float maxTime() {
		float max = 5f;
		for (ActionInterval a : actions) {
			max = Math.max(max, a.end());
		}
		for (RotationKeyframe r : rotations) {
			max = Math.max(max, r.time());
		}
		return max;
	}

	private static int rowY(int row) {
		return ROWS_Y + row * ROW_HEIGHT;
	}

	private static int timeToX(float seconds) {
		return TIMELINE_X + Math.round(seconds * PIXELS_PER_SECOND);
	}

	private static float xToTime(double x) {
		return Math.max(0f, (float) (x - TIMELINE_X) / PIXELS_PER_SECOND);
	}

	private static float xToTimeSnapped(double x) {
		float raw = xToTime(x);
		return Math.round(raw / TICK_SECONDS) * TICK_SECONDS;
	}

	private static String label(ActionType type) {
		return switch (type) {
			case FORWARD -> "Forward";
			case BACK -> "Back";
			case LEFT -> "Left";
			case RIGHT -> "Right";
			case JUMP -> "Jump";
			case SNEAK -> "Sneak";
			case SPRINT -> "Sprint";
			case ATTACK -> "Attack";
			case USE -> "Use";
			case SWAP_HANDS -> "Swap Hands";
			case DROP -> "Drop";
		};
	}

	private static String formatSeconds(float seconds) {
		return (Math.round(seconds * 100) / 100.0) + "s";
	}

	private static float parse(String value, float fallback) {
		try {
			return Float.parseFloat(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private Button disabled(String text, int x, int y, int width) {
		Button button = Button.builder(Component.literal(text), b -> {}).bounds(x, y, width, ROW_HEIGHT).build();
		button.active = false;
		return button;
	}
}
