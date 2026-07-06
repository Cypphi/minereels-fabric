package dev.cypphi.minereels.client;

import dev.cypphi.minereels.MineReels;
import dev.cypphi.minereels.config.OverlayConfig;
import dev.cypphi.minereels.reel.Reel;
import dev.cypphi.minereels.reel.ReelProvider;
import dev.cypphi.minereels.video.FfmpegAudioStream;
import dev.cypphi.minereels.video.FfmpegVideoStream;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A movable, resizable HUD overlay that renders a vertical (9:16) reel card.
 *
 * The HUD element is passive normally, but while the chat screen is open the
 * card can be left-click-dragged to move, scrolled to resize, and right-clicked
 * to hide/show. Card text scales with the card size. Feed navigation and liking
 * are driven by key bindings.
 *
 * Backed by whichever {@link ReelProvider} is passed in (mock or Instagram);
 * the renderer doesn't care which.
 */
public final class ReelFeedOverlay {
	private static final double MIN_SCALE = 5.0;
	private static final double MAX_SCALE = 60.0;
	private static final double SCROLL_STEP = 2.0;
	private static final int MIN_CARD_WIDTH = 60;
	private static final double REEL_ASPECT = 16.0 / 9.0; // height / width
	private static final int PADDING = 6;
	private static final float TEXT_REF_WIDTH = 180f; // card width at which text is 1:1

	private final ReelProvider provider;
	private final ReelTextureCache textures = new ReelTextureCache();
	private final List<Reel> reels = new ArrayList<>();

	// Video playback for the currently-shown reel.
	private String playingId;
	private String playingUrl;
	private FfmpegVideoStream videoStream;
	private FfmpegAudioStream audioStream;
	private VideoSurface videoSurface;
	private int playingVideoWidth;
	private int playingVideoHeight;
	private int playingMaxVideoFps;
	private int videoSequence;
	private int index = 0;
	private String nextCursor = null;
	private boolean loading = false;

	private boolean dragging = false;
	private double grabOffsetX = 0;
	private double grabOffsetY = 0;
	private boolean chatOpen = false;

	public ReelFeedOverlay(ReelProvider provider) {
		this.provider = provider;
	}

	private Reel current() {
		return index >= 0 && index < reels.size() ? reels.get(index) : null;
	}

	// --- HUD rendering -------------------------------------------------------

	public void render(DrawContext context, RenderTickCounter tickCounter) {
		OverlayConfig config = OverlayConfig.get();
		if (!config.enabled) {
			stopVideo();
			return;
		}
		// Right-click hides the overlay; still show it (dimmed) while chat is open.
		if (!config.showInHud && !chatOpen) {
			stopVideo();
			return;
		}

		ensureLoaded();
		Reel reel = current();
		if (reel == null) {
			stopVideo();
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Rect rect = currentRect(context.getScaledWindowWidth(), context.getScaledWindowHeight(), config);

		manageVideo(reel);
		drawCard(context, client, reel, rect, config);

		if (chatOpen) {
			context.drawStrokedRectangle(rect.x, rect.y, rect.w, rect.h, 0xAAFFFFFF);
			context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, config.showInHud ? 0x18FFFFFF : 0x55FF3333);
		}
	}

	private void drawCard(DrawContext context, MinecraftClient client, Reel reel, Rect rect, OverlayConfig config) {
		context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0xCC101014);

		// Video if a frame is ready, otherwise the still cover image.
		boolean drewVideo = false;
		if (videoSurface != null && videoSurface.hasFrame()) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, videoSurface.id(),
					rect.x, rect.y, 0f, 0f, rect.w, rect.h, videoSurface.width(), videoSurface.height(),
					videoSurface.width(), videoSurface.height());
			drewVideo = true;
		}
		if (!drewVideo) {
			textures.ensure(reel.id(), reel.thumbnailUrl());
			ReelTextureCache.Entry tex = textures.get(reel.id());
			if (tex != null) {
				context.drawTexture(RenderPipelines.GUI_TEXTURED, tex.id(),
						rect.x, rect.y, 0f, 0f, rect.w, rect.h, tex.width(), tex.height(), tex.width(), tex.height());
			}
		}
		context.drawStrokedRectangle(rect.x, rect.y, rect.w, rect.h, 0xFF2A2A32);

		TextRenderer font = client.textRenderer;
		float scale = textScale(rect.w);
		int lineH = Math.round(font.fontHeight * scale);
		int pad = PADDING;

		// Header: author + feed position.
		drawScaledText(context, font, "@" + reel.author(), rect.x + pad, rect.y + pad, 0xFFFFFFFF, scale);

		// Caption, truncated to the card width.
		String caption = truncate(font, reel.caption(), (rect.w - pad * 2) / scale);
		drawScaledText(context, font, caption, rect.x + pad, rect.y + pad + lineH + 4, 0xFFDDDDDD, scale);

		// Footer: like state + count.
		String heart = reel.liked() ? "♥" : "♡";
		int likeColor = reel.liked() ? 0xFFFF4D6D : 0xFFFFFFFF;
		float footerY = rect.y + rect.h - pad - lineH;
		drawScaledText(context, font, heart, rect.x + pad, footerY, likeColor, scale);
		float heartWidth = font.getWidth(heart) * scale;
		drawScaledText(context, font, formatLikes(reel.likeCount()), rect.x + pad + heartWidth + 4, footerY, 0xFFFFFFFF, scale);
	}

	private void drawScaledText(DrawContext context, TextRenderer font, String text, float x, float y, int color, float scale) {
		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale);
		context.drawText(font, text, 0, 0, color, true);
		matrices.popMatrix();
	}

	private float textScale(int cardWidth) {
		float s = cardWidth / TEXT_REF_WIDTH;
		return Math.max(0.6f, Math.min(3.0f, s));
	}

	private Rect currentRect(int screenW, int screenH, OverlayConfig config) {
		int width = Math.max(MIN_CARD_WIDTH, (int) Math.round(screenW * (config.scalePercent / 100.0)));
		int height = (int) Math.round(width * REEL_ASPECT);
		if (height > screenH) {
			height = screenH;
			width = Math.max(MIN_CARD_WIDTH, (int) Math.round(height / REEL_ASPECT));
		}
		int x = (int) Math.round(screenW * (config.xPercent / 100.0));
		int y = (int) Math.round(screenH * (config.yPercent / 100.0));
		x = clamp(x, 0, Math.max(0, screenW - width));
		y = clamp(y, 0, Math.max(0, screenH - height));
		return new Rect(x, y, width, height);
	}

	// --- Chat-screen interaction --------------------------------------------

	public void registerChatInteraction() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof ChatScreen)) {
				chatOpen = false;
				return;
			}
			chatOpen = true;
			ScreenEvents.remove(screen).register(removed -> chatOpen = false);
			ScreenMouseEvents.afterMouseClick(screen).register(this::onMouseClick);
			ScreenMouseEvents.afterMouseDrag(screen).register(this::onMouseDrag);
			ScreenMouseEvents.afterMouseRelease(screen).register(this::onMouseRelease);
			ScreenMouseEvents.allowMouseScroll(screen).register(this::onMouseScroll);
		});
	}

	private boolean onMouseClick(Screen screen, Click click, boolean consumed) {
		OverlayConfig config = OverlayConfig.get();
		if (!config.enabled) {
			return consumed;
		}
		Rect rect = currentRect(screen.width, screen.height, config);
		if (!rect.contains(click.x(), click.y())) {
			return consumed;
		}
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			config.showInHud = !config.showInHud;
			dragging = false;
			OverlayConfig.save();
			return true;
		}
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = true;
			grabOffsetX = click.x() - rect.x;
			grabOffsetY = click.y() - rect.y;
			return true;
		}
		return consumed;
	}

	private boolean onMouseDrag(Screen screen, Click click, double dx, double dy, boolean consumed) {
		if (!dragging || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return consumed;
		}
		OverlayConfig config = OverlayConfig.get();
		double w = Math.max(1.0, screen.width);
		double h = Math.max(1.0, screen.height);
		config.xPercent = clampD((click.x() - grabOffsetX) / w * 100.0, 0.0, 100.0);
		config.yPercent = clampD((click.y() - grabOffsetY) / h * 100.0, 0.0, 100.0);
		return true;
	}

	private boolean onMouseRelease(Screen screen, Click click, boolean consumed) {
		if (!dragging || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return consumed;
		}
		dragging = false;
		OverlayConfig.save();
		return true;
	}

	private boolean onMouseScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical == 0.0) {
			return true;
		}
		OverlayConfig config = OverlayConfig.get();
		Rect rect = currentRect(screen.width, screen.height, config);
		if (!rect.contains(mouseX, mouseY)) {
			return true;
		}
		config.scalePercent = clampD(config.scalePercent + vertical * SCROLL_STEP, MIN_SCALE, MAX_SCALE);
		OverlayConfig.save();
		return false; // consume so chat history doesn't also scroll
	}

	// --- Feed navigation (called from key bindings) -------------------------

	public void nextReel() {
		if (!OverlayConfig.get().enabled) {
			return;
		}
		if (index < reels.size() - 1) {
			index++;
		}
		// Infinite feed: prefetch the next page as we approach the end.
		if (nextCursor != null && index >= reels.size() - 3) {
			fetchNextPage();
		}
	}

	public void previousReel() {
		if (!OverlayConfig.get().enabled) {
			return;
		}
		if (index > 0) {
			index--;
		}
	}

	public void toggleEnabled() {
		OverlayConfig config = OverlayConfig.get();
		config.enabled = !config.enabled;
		OverlayConfig.save();
	}

	public void likeCurrent() {
		Reel reel = current();
		if (reel == null) {
			return;
		}
		boolean newState = !reel.liked();
		reels.set(index, reel.withLiked(newState));
		provider.toggleLike(reel.id(), newState).whenComplete((actualState, error) ->
				MinecraftClient.getInstance().execute(() -> applyLikeResult(reel, actualState, error)));
	}

	private void applyLikeResult(Reel original, Boolean actualState, Throwable error) {
		int reelIndex = findReelIndex(original.id());
		if (reelIndex < 0) {
			return;
		}
		if (error != null) {
			reels.set(reelIndex, reels.get(reelIndex).withLiked(original.liked()));
			MineReels.LOGGER.warn("Failed to toggle Instagram like for {}", original.id(), error);
			return;
		}
		reels.set(reelIndex, reels.get(reelIndex).withLiked(Boolean.TRUE.equals(actualState)));
	}

	private int findReelIndex(String reelId) {
		for (int i = 0; i < reels.size(); i++) {
			if (reels.get(i).id().equals(reelId)) {
				return i;
			}
		}
		return -1;
	}

	// --- Video playback ------------------------------------------------------

	private void manageVideo(Reel reel) {
		String url = reel.videoUrl();
		if (url == null || url.isBlank()) {
			stopVideo();
			return;
		}
		OverlayConfig config = OverlayConfig.get();
		if (!config.playVideos) {
			stopVideo();
			return;
		}

		int videoWidth = config.videoWidthPixels();
		int videoHeight = config.videoHeightPixels();
		int maxVideoFps = config.maxVideoFps();
		if (!reel.id().equals(playingId) || !url.equals(playingUrl)
				|| videoStream == null || videoSurface == null
				|| playingVideoWidth != videoWidth || playingVideoHeight != videoHeight
				|| playingMaxVideoFps != maxVideoFps) {
			stopVideo();
			playingId = reel.id();
			playingUrl = url;
			playingVideoWidth = videoWidth;
			playingVideoHeight = videoHeight;
			playingMaxVideoFps = maxVideoFps;
			videoStream = new FfmpegVideoStream(url, videoWidth, videoHeight, maxVideoFps);
			videoStream.start();
			videoSurface = new VideoSurface(videoStream, videoWidth, videoHeight, videoSequence++, maxVideoFps);
		}
		manageAudio(url, config);
		if (videoSurface != null) {
			videoSurface.uploadIfReady();
		}
	}

	private void manageAudio(String url, OverlayConfig config) {
		if (!config.playAudio || config.volumePercent <= 0.0) {
			stopAudio();
			return;
		}
		if (audioStream == null) {
			audioStream = new FfmpegAudioStream(url, () -> OverlayConfig.get().volumePercent / 100.0);
			audioStream.start();
		}
	}

	private void stopVideo() {
		if (videoStream != null) {
			videoStream.stop();
			videoStream = null;
		}
		stopAudio();
		if (videoSurface != null) {
			videoSurface.close();
			videoSurface = null;
		}
		playingId = null;
		playingUrl = null;
		playingVideoWidth = 0;
		playingVideoHeight = 0;
		playingMaxVideoFps = 0;
	}

	private void stopAudio() {
		if (audioStream != null) {
			audioStream.stop();
			audioStream = null;
		}
	}

	// --- Feed loading --------------------------------------------------------

	private void ensureLoaded() {
		if (reels.isEmpty() && !loading) {
			fetchNextPage();
		}
	}

	private void fetchNextPage() {
		if (loading) {
			return;
		}
		loading = true;
		provider.fetchFeed(nextCursor).whenComplete((page, error) -> MinecraftClient.getInstance().execute(() -> {
			loading = false;
			if (error != null || page == null) {
				return;
			}
			reels.addAll(page.reels());
			nextCursor = page.nextCursor();
		}));
	}

	// --- helpers -------------------------------------------------------------

	private String truncate(TextRenderer font, String text, float maxWidth) {
		if (font.getWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int end = text.length();
		while (end > 0 && font.getWidth(text.substring(0, end) + ellipsis) > maxWidth) {
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}

	private String formatLikes(long count) {
		if (count >= 1_000_000) {
			return String.format("%.1fM", count / 1_000_000.0);
		}
		if (count >= 1_000) {
			return String.format("%.1fK", count / 1_000.0);
		}
		return String.valueOf(count);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static double clampD(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx <= x + w && my <= y + h;
		}
	}
}
