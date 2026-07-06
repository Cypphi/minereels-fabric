package dev.cypphi.minereels.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.cypphi.minereels.MineReels;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted state for the reel overlay: where it sits, how big it is, and the
 * Instagram session cookie.
 *
 * Pure data + JSON I/O, no Minecraft classes, so it lives in the common source
 * set and is shared by every version's client code. Position is stored as a
 * percentage of screen size so it survives resolution/GUI-scale changes.
 */
public final class OverlayConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(MineReels.MOD_ID + ".json");

	private static OverlayConfig instance;

	/** Master on/off toggle. */
	public boolean enabled = true;

	/** When false, the overlay is hidden in the HUD (right-click while chat is open). */
	@SerializedName("show-in-hud")
	public boolean showInHud = true;

	/** Top-left of the overlay as a percentage (0-100) of the screen. */
	@SerializedName("x-percent")
	public double xPercent = 2.0;
	@SerializedName("y-percent")
	public double yPercent = 5.0;

	/** Card width as a percentage (5-60) of screen width; height follows 9:16. */
	@SerializedName("scale-percent")
	public double scalePercent = 18.0;

	/** Playback volume as a percentage (0-100) for reel audio. */
	@SerializedName("volume-percent")
	public double volumePercent = 100.0;

	/** Decode and upload video frames at no more than this FPS. */
	@SerializedName("max-video-fps")
	public double maxVideoFps = 24.0;

	/** Height, in pixels, of the decoded 9:16 video texture. */
	@SerializedName("video-height-pixels")
	public double videoHeightPixels = 720.0;

	/** When false, only thumbnails are rendered and no video FFmpeg process starts. */
	@SerializedName("play-videos")
	public boolean playVideos = true;

	/** When false, the separate audio FFmpeg process is not started. */
	@SerializedName("play-audio")
	public boolean playAudio = true;

	/** Reel-to-reel vertical scroll animation duration in milliseconds. */
	@SerializedName("scroll-animation-millis")
	public double scrollAnimationMillis = 150.0;

	/**
	 * Instagram session cookie (the full {@code Cookie:} header value from a
	 * logged-in browser). When blank, the mock feed is used; when set, the real
	 * home reels feed is fetched. Never committed — lives only in local config.
	 */
	@SerializedName("instagram-cookie")
	public String instagramCookie = "";

	public static OverlayConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public int maxVideoFps() {
		return clamp((int) Math.round(maxVideoFps), 5, 60);
	}

	public int videoHeightPixels() {
		return clamp((int) Math.round(videoHeightPixels), 240, 1080);
	}

	public int videoWidthPixels() {
		return Math.max(1, (int) Math.round(videoHeightPixels() * 9.0 / 16.0));
	}

	public int scrollAnimationMillis() {
		return clamp((int) Math.round(scrollAnimationMillis), 0, 1000);
	}

	private static OverlayConfig load() {
		if (!Files.exists(PATH)) {
			return new OverlayConfig();
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			OverlayConfig loaded = GSON.fromJson(reader, OverlayConfig.class);
			return loaded != null ? loaded : new OverlayConfig();
		} catch (Exception e) {
			MineReels.LOGGER.warn("Failed to read overlay config, using defaults", e);
			return new OverlayConfig();
		}
	}

	public static void save() {
		if (instance == null) {
			return;
		}
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			MineReels.LOGGER.warn("Failed to save overlay config", e);
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
