package dev.cypphi.minereels.client;

import dev.cypphi.minereels.MineReels;
import dev.cypphi.minereels.video.FfmpegVideoStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * A GL texture fed by a {@link FfmpegVideoStream}. A background worker converts
 * raw RGBA frames into the backing NativeImage (the expensive per-pixel work),
 * and the render thread only calls {@link #uploadIfReady()} — a single GL
 * upload — so decoding/copying never stalls the frame loop. The fill and the
 * upload share a lock, so the render thread waits at most one fill (~1-2 ms)
 * rather than doing the whole copy itself every frame.
 */
public final class VideoSurface {
	private final int width;
	private final int height;
	private final int frameSize;
	private final Identifier id;
	private final NativeImageBackedTexture texture;
	private final NativeImage image;
	private final FfmpegVideoStream stream;

	private final Object lock = new Object();
	private volatile boolean running = true;
	private volatile boolean everFilled = false;
	private boolean dirty = false;
	private byte[] lastConsumed;
	private final Thread worker;

	public VideoSurface(FfmpegVideoStream stream, int width, int height, int sequence) {
		this.stream = stream;
		this.width = width;
		this.height = height;
		this.frameSize = width * height * 4;
		this.texture = new NativeImageBackedTexture(() -> "minereels-video", width, height, false);
		this.image = texture.getImage();
		this.id = Identifier.of(MineReels.MOD_ID, "reel_video_" + sequence);
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
		this.worker = new Thread(this::run, "minereels-video-fill");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	private void run() {
		while (running) {
			byte[] frame = stream.latestFrame();
			if (frame != null && frame != lastConsumed && frame.length >= frameSize) {
				synchronized (lock) {
					fill(frame);
					dirty = true;
				}
				lastConsumed = frame;
				everFilled = true;
			}
			try {
				Thread.sleep(8);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void fill(byte[] rgba) {
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int r = rgba[i] & 0xFF;
				int g = rgba[i + 1] & 0xFF;
				int b = rgba[i + 2] & 0xFF;
				int a = rgba[i + 3] & 0xFF;
				i += 4;
				image.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
	}

	/** Render-thread: upload the latest filled frame if there is one. */
	public void uploadIfReady() {
		synchronized (lock) {
			if (dirty) {
				texture.upload();
				dirty = false;
			}
		}
	}

	public boolean hasFrame() {
		return everFilled;
	}

	public Identifier id() {
		return id;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public void close() {
		running = false;
		worker.interrupt();
		MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
	}
}
