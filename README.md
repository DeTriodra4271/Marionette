# Marionette

A client-side Fabric mod for Minecraft that records your movement and actions, replays them, and lets you build smooth camera paths and edit takes on a timeline.

## Features

- **Record and replay takes.** Capture what you do (movement, jumps, sneaking, sprinting, attacking, using, swapping hands, dropping items, and where you look) and play it back later. Replay presses the same keys and lets the game's own physics move you, so jumps and sprints behave like the real thing.
- **Takes menu.** Play, edit, rename, or delete saved takes. Optional looping.
- **Action timeline editor.** Open a take as blocks on a timeline. Drag blocks to move them, drag either edge to trim, add new actions, and add or retime look keyframes.
- **Keyframe camera paths.** Drop keyframes as you fly around, then play them back as a smooth spline (Catmull-Rom for position, shortest-way interpolation for yaw). Useful for cinematics.

## Controls

All keys can be rebound under **Options → Controls → Marionette**.

| Key | Action |
|---|---|
| R | Start / stop recording (you name the take when you stop) |
| P | Play the latest take / stop playback |
| M | Open the Takes menu |
| O | Open the action timeline for the latest take |
| K | Add a keyframe at your current position and view |
| L | Finish the path and save it (you name it) |
| I | Play the latest keyframe path / stop |
| U | Open the Keyframe Paths menu |

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 25

The mod is client-only; it does not need to be installed on a server.

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API and the Marionette jar in your `mods` folder.
3. Launch the game and bind the keys if the defaults clash with other mods.

## Where data is saved

Takes and paths are plain JSON files under `config/marionette/` inside your game folder (`recordings/` for takes, `keyframes/` for camera paths). You can back them up or share them by copying the files.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. To start a development client:

```
./gradlew runClient
```

## Fair use

Replaying recorded input on a multiplayer server can count as a macro or automation, which many servers forbid. Check the server's rules before using it there. It is intended for single-player, private worlds, and content creation.

## Roadmap

- Speed control for playback
- Path preview in the world
- More editing tools on the timeline

## License

[CC0 1.0](LICENSE): public domain, do what you like.
