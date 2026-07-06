<p align="center">
  <img src="src/main/resources/assets/minereels/icon.png" alt="MineReels icon" width="96" height="96">
</p>

<h1 align="center">MineReels</h1>

Scroll your Instagram Reels feed inside Minecraft from a lightweight Fabric
client mod — a movable, resizable overlay that plays reels (video + audio)
right over the HUD.

This project works by replaying your own logged-in Instagram web 
session, which is against Instagram's Terms of Service —
use a throwaway account and expect to refresh it.

## Requirements

- **Minecraft 1.21.11** with **Fabric Loader** (primary target; see
  [Versions](#versions)).
- **Fabric API**.
- **[YetAnotherConfigLib (YACL)]** and **[Mod Menu]** — for the in-game config
  screen (used to paste your token).
- **`ffmpeg`** installed and on your `PATH` — required for reel video/audio
  playback. Check with `ffmpeg -version`.
- **Java 21**.

[YetAnotherConfigLib (YACL)]: https://modrinth.com/mod/yacl
[Mod Menu]: https://modrinth.com/mod/modmenu

## Installation

1. Install Fabric Loader for 1.21.11.
2. Drop these into your `mods/` folder: **MineReels**, **Fabric API**,
   **YetAnotherConfigLib**, and **Mod Menu**.
3. Make sure `ffmpeg` is installed (e.g. `sudo pacman -S ffmpeg`,
   `apt install ffmpeg`, `brew install ffmpeg`, or the Windows build on PATH).
4. Launch the game once so the mod writes its config file, then add your token
   (below).

## Getting your Instagram session cookie

MineReels doesn't log in — you paste the cookie from a browser where you're
already logged into Instagram, and the mod reuses that session.

1. In a desktop browser, log into <https://www.instagram.com> **on a throwaway
   account**.
2. Open **DevTools** (`F12`) → **Network** tab. Reload the page if it's empty.
3. Click any request to `www.instagram.com` in the list.
4. In **Headers → Request Headers**, find the **`Cookie:`** line and copy its
   **entire** value (it's a long string containing `sessionid=…`,
   `csrftoken=…`, `ds_user_id=…`, etc.). Copy the whole thing, not just one
   part.
5. Paste it into the mod (below). Keep it secret — it grants full access to that
   account.

> The token expires when you log that browser session out or change the
> password. If reels stop loading, grab a fresh cookie and paste it again.

## Adding your token

**In-game (recommended):** pause menu / title screen → **Mods** (Mod Menu) →
**MineReels** → **Config** → **Tokens** tab → paste into **Instagram cookie** →
save. Then **restart the game** — the feed provider is created at startup, so a
newly pasted cookie takes effect on the next launch.

**By file:** edit `config/minereels.json` in your game directory and set
`"instagram-cookie"` to the cookie string, then launch.

## Using the mod

The overlay appears on the HUD once a cookie is set. It's passive during normal
play; **open chat** to interact with it directly:

| Action | Control |
| --- | --- |
| Move the card | Left-click **drag** (chat open) |
| Resize the card | **Scroll** over it (chat open) |
| Hide / show the card | **Right-click** it (chat open) |
| Next reel | **↓** arrow |
| Previous reel | **↑** arrow |
| Like current reel | **L** |
| Toggle overlay on/off | **Insert** |
| Multi-tap action (**→** arrow) | 1 tap = next · 2 taps = like · 3 taps = previous |

All key bindings are rebindable in **Options → Controls → MineReels**.

The feed is infinite — it fetches more reels as you scroll toward the end, like
the real app. Each reel plays its video with audio (scaled by the volume
slider); scrolling to a new reel starts it playing.

## Configuration

Config lives in `config/minereels.json` and is editable in the YACL screen.

**General tab:** enable/disable the overlay, show/hide in HUD, card size (%),
volume (%), and X/Y position (%).

**Tokens tab:** your Instagram cookie.

## Security & notes

- Your session cookie is a **live credential**. Never share `config/minereels.json`
  or your logs, and never commit them (both `run/` and `*.har` are gitignored).
- Using an unofficial client against Instagram violates its ToS and can get an
  account restricted or banned — use a burner and rate-limit your scrolling.
- Media URLs from Instagram are short-lived and fetched on demand.

## Build

Requires Java 21.

```sh
./gradlew build            # all versions
./gradlew :1.21.11:build   # primary target only
```
