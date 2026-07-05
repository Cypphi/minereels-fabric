package dev.cypphi.minereels.client;

import dev.cypphi.minereels.MineReels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads reel still images (thumbnails / cover frames) off-thread, decodes
 * them, and uploads them to GL textures on the render thread. This is the same
 * download → NativeImage → texture → blit path a video player will drive per
 * frame, so it's the foundation for playback.
 *
 * Instagram serves JPEG, and NativeImage.read only accepts PNG, so we decode
 * with ImageIO and copy pixels into a NativeImage via setColorArgb (which takes
 * ARGB and handles the internal ABGR ordering).
 */
public final class ReelTextureCache {
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final Map<String, Entry> loaded = new ConcurrentHashMap<>();
	private final Set<String> loading = ConcurrentHashMap.newKeySet();
	private int sequence;

	/** A ready texture plus its pixel dimensions. */
	public record Entry(Identifier id, int width, int height) {
	}

	public Entry get(String key) {
		return loaded.get(key);
	}

	/** Kick off an async load for the given key/url if not already loaded/loading. */
	public void ensure(String key, String url) {
		if (url == null || url.isBlank() || loaded.containsKey(key) || !loading.add(key)) {
			return;
		}
		CompletableFuture
				.supplyAsync(() -> decode(download(url)))
				.whenComplete((image, error) -> MinecraftClient.getInstance().execute(() -> {
					try {
						if (image == null || error != null) {
							return;
						}
						NativeImageBackedTexture texture =
								new NativeImageBackedTexture(() -> "minereels-" + key, image);
						Identifier id = Identifier.of(MineReels.MOD_ID, "reel_tex_" + (sequence++));
						MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
						loaded.put(key, new Entry(id, image.getWidth(), image.getHeight()));
					} finally {
						loading.remove(key);
					}
				}));
	}

	private byte[] download(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(20))
					.header("User-Agent", "Mozilla/5.0")
					.GET()
					.build();
			HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
			return response.statusCode() / 100 == 2 ? response.body() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** Decode arbitrary image bytes (JPEG/PNG) into a NativeImage off-thread. */
	private NativeImage decode(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		try {
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
			if (src == null) {
				return null;
			}
			int w = src.getWidth();
			int h = src.getHeight();
			NativeImage image = new NativeImage(w, h, false);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					image.setColorArgb(x, y, src.getRGB(x, y));
				}
			}
			return image;
		} catch (Exception e) {
			MineReels.LOGGER.warn("Failed to decode reel image", e);
			return null;
		}
	}
}
