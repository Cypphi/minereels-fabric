package dev.cypphi.minereels.reel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.cypphi.minereels.MineReels;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the authenticated home reels feed from instagram.com by replaying the
 * private web-API calls the site itself makes. Auth is entirely by the session
 * cookie the user pastes in config — no login flow.
 *
 * The feed is a Relay connection paginated by an opaque {@code after} cursor.
 * Page 1 and the anti-CSRF tokens ({@code fb_dtsg}/{@code lsd}) are only
 * available from the reels page HTML, so the first fetch bootstraps them; later
 * pages use the GraphQL pagination query. All I/O runs off-thread.
 *
 * Endpoints, doc_ids and field paths were reverse-engineered from a HAR capture
 * and will drift as Instagram changes them.
 */
public final class InstagramProvider implements ReelProvider {
	private static final String APP_ID = "936619743392459";
	private static final String ASBD_ID = "359341";
	private static final String USER_AGENT =
			"Mozilla/5.0 (X11; Linux x86_64; rv:152.0) Gecko/20100101 Firefox/152.0";
	private static final String GRAPHQL = "https://www.instagram.com/graphql/query";
	// The like mutation is served by /api/graphql; posting it to /graphql/query
	// returns ok but silently no-ops.
	private static final String API_GRAPHQL = "https://www.instagram.com/api/graphql";
	private static final String REELS_PAGE = "https://www.instagram.com/reels/";
	private static final String FEED_DOC_ID = "36825039943776829";
	private static final String FEED_FRIENDLY = "PolarisClipsTabDesktopPaginationQuery";
	private static final String LIKE_DOC_ID = "27182485238052618";
	private static final String LIKE_FRIENDLY = "usePolarisLikeMediaXIGLikeMutation";
	private static final String UNLIKE_DOC_ID = "27345296031770102";
	private static final String UNLIKE_FRIENDLY = "usePolarisLikeMediaXIGUnlikeMutation";
	private static final String CONNECTION = "xdt_api__v1__clips__home__connection_v2";
	private static final int PAGE_SIZE = 10;

	private static final Pattern COOKIE_CSRF = Pattern.compile("csrftoken=([^;]+)");
	private static final Pattern FB_DTSG = Pattern.compile("\"dtsg\":\\{\"token\":\"([^\"]+)\"");
	private static final Pattern LSD = Pattern.compile("\"LSD\",\\[],\\{\"token\":\"([^\"]+)\"");
	private static final Pattern ACTOR = Pattern.compile("\"actorID\":\"(\\d+)\"");
	private static final Pattern HASTE = Pattern.compile("\"haste_session\":\"([^\"]+)\"");
	private static final Pattern HSI = Pattern.compile("\"hsi\":\"([^\"]+)\"");
	private static final Pattern REV = Pattern.compile("\"client_revision\":(\\d+)");
	private static final Pattern SPIN_T = Pattern.compile("\"__spin_t\":(\\d+)");

	private final String cookie;
	private final String csrfToken;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "minereels-instagram");
		t.setDaemon(true);
		return t;
	});
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	// Anti-CSRF tokens + actor id, scraped from the reels page on first use.
	private volatile boolean bootstrapped = false;
	private String fbDtsg;
	private String lsd;
	private String actorId;
	private String hasteSession;
	private String hsi;
	private String clientRevision;
	private String spinT;
	private final AtomicInteger requestCounter = new AtomicInteger(10);
	private final AtomicInteger mutationCounter = new AtomicInteger(1);

	// Media ids already shown, echoed back so the feed query doesn't repeat them.
	private final Set<String> seenReels = new LinkedHashSet<>();

	// pk -> organic_tracking_token from the feed; required to register a like.
	private final Map<String, String> trackingTokens = new ConcurrentHashMap<>();
	private final Map<String, String> reelCodes = new ConcurrentHashMap<>();

	public InstagramProvider(String cookie) {
		this.cookie = cookie;
		this.csrfToken = firstGroup(COOKIE_CSRF, cookie);
	}

	@Override
	public String name() {
		return "instagram";
	}

	@Override
	public CompletableFuture<ReelPage> fetchFeed(String cursor) {
		return CompletableFuture.supplyAsync(() -> {
			ensureBootstrapped();
			String body = post(GRAPHQL, FEED_DOC_ID, FEED_FRIENDLY, feedVariables(cursor));
			return parseFeed(body);
		}, executor);
	}

	@Override
	public CompletableFuture<Boolean> toggleLike(String reelId, boolean liked) {
		return CompletableFuture.supplyAsync(() -> {
			ensureBootstrapped();
			String token = trackingTokens.get(reelId);
			if (token == null || token.isBlank()) {
				throw new IllegalStateException("Missing Instagram tracking token for reel " + reelId);
			}

			String docId = liked ? LIKE_DOC_ID : UNLIKE_DOC_ID;
			String friendly = liked ? LIKE_FRIENDLY : UNLIKE_FRIENDLY;
			String body = post(API_GRAPHQL, docId, friendly, likeVariables(reelId, token, liked), refererFor(reelId));
			boolean actualState = parseLikedState(body, liked);
			if (actualState != liked) {
				MineReels.LOGGER.warn("Instagram returned liked={} after requesting liked={} for {}", actualState, liked, reelId);
			}
			return actualState;
		}, executor);
	}

	// --- bootstrap -----------------------------------------------------------

	private synchronized void ensureBootstrapped() {
		if (bootstrapped) {
			return;
		}
		String html = get(REELS_PAGE);
		fbDtsg = firstGroup(FB_DTSG, html);
		lsd = firstGroup(LSD, html);
		actorId = firstGroup(ACTOR, html);
		hasteSession = firstGroup(HASTE, html);
		hsi = firstGroup(HSI, html);
		clientRevision = firstGroup(REV, html);
		spinT = firstGroup(SPIN_T, html);
		if (fbDtsg == null || lsd == null) {
			MineReels.LOGGER.warn("Instagram bootstrap could not find fb_dtsg/lsd — cookie may be invalid or the page markup changed");
		}
		bootstrapped = true;
	}

	// --- request building ----------------------------------------------------

	private String feedVariables(String cursor) {
		JsonArray seen = new JsonArray();
		for (String id : seenReels) {
			JsonObject seenItem = new JsonObject();
			seenItem.addProperty("id", id);
			seen.add(seenItem);
		}
		JsonObject data = new JsonObject();
		data.addProperty("container_module", "clips_tab_desktop_page");
		data.addProperty("seen_reels", seen.toString());

		JsonObject variables = new JsonObject();
		variables.add("after", cursor != null ? new JsonPrimitive(cursor) : JsonNull.INSTANCE);
		variables.add("before", JsonNull.INSTANCE);
		variables.add("data", data);
		variables.addProperty("first", PAGE_SIZE);
		variables.add("last", JsonNull.INSTANCE);
		variables.addProperty("__relay_internal__pv__PolarisReelsRecoDebugOverlayEnabledrelayprovider", false);
		variables.addProperty("__relay_internal__pv__PolarisAIGMMediaWebLabelEnabledrelayprovider", false);
		return variables.toString();
	}

	private String post(String endpoint, String docId, String friendlyName, String variables) {
		return post(endpoint, docId, friendlyName, variables, REELS_PAGE);
	}

	private String post(String endpoint, String docId, String friendlyName, String variables, String referer) {
		String rev = orEmpty(clientRevision);
		String form = "av=" + enc(orEmpty(actorId))
				+ "&__a=1"
				+ "&__req=" + enc(nextRequestId())
				+ "&__hs=" + enc(orEmpty(hasteSession))
				+ "&dpr=1"
				+ "&__ccg=EXCELLENT"
				+ "&__rev=" + enc(rev)
				+ "&__comet_req=7"
				+ (hsi != null ? "&__hsi=" + enc(hsi) : "")
				+ "&fb_dtsg=" + enc(orEmpty(fbDtsg))
				+ "&jazoest=" + jazoest(orEmpty(fbDtsg))
				+ "&lsd=" + enc(orEmpty(lsd))
				+ "&__spin_r=" + enc(rev)
				+ "&__spin_b=trunk"
				+ "&__spin_t=" + enc(orEmpty(spinT))
				+ "&__user=0"
				+ "&__d=www"
				+ "&fb_api_caller_class=RelayModern"
				+ "&fb_api_req_friendly_name=" + enc(friendlyName)
				+ "&variables=" + enc(variables)
				+ "&server_timestamps=true"
				+ "&doc_id=" + enc(docId);

		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofSeconds(20))
				.header("Accept", "*/*")
				.header("Accept-Language", "en-US,en;q=0.9")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("User-Agent", USER_AGENT)
				.header("Cookie", cookie)
				.header("Referer", referer)
				.header("Origin", "https://www.instagram.com")
				.header("X-IG-App-ID", APP_ID)
				.header("X-IG-Max-Touch-Points", "0")
				.header("X-ASBD-ID", ASBD_ID)
				.header("X-FB-Friendly-Name", friendlyName)
				.header("Sec-Fetch-Dest", "empty")
				.header("Sec-Fetch-Mode", "cors")
				.header("Sec-Fetch-Site", "same-origin");
		if (csrfToken != null) {
			builder.header("X-CSRFToken", csrfToken);
		}
		if (lsd != null) {
			builder.header("X-FB-LSD", lsd);
		}
		return send(builder.POST(HttpRequest.BodyPublishers.ofString(form)).build());
	}

	private String get(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", USER_AGENT)
				.header("Cookie", cookie)
				.header("X-IG-App-ID", APP_ID)
				.GET()
				.build();
		return send(request);
	}

	private String likeVariables(String reelId, String trackingToken, boolean liked) {
		JsonObject input = new JsonObject();
		input.addProperty("actor_id", orEmpty(actorId));
		input.addProperty("client_mutation_id", String.valueOf(mutationCounter.getAndIncrement()));
		if (liked) {
			input.addProperty("container_module", "reels_tab");
		}
		input.addProperty("media_id", reelId);
		input.addProperty("tracking_token", trackingToken);

		JsonObject variables = new JsonObject();
		variables.add("input", input);
		return variables.toString();
	}

	private String refererFor(String reelId) {
		String code = reelCodes.get(reelId);
		return code == null || code.isBlank() ? REELS_PAGE : REELS_PAGE + code + "/";
	}

	private String nextRequestId() {
		return Integer.toString(requestCounter.getAndIncrement(), 36);
	}

	private String send(HttpRequest request) {
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Instagram returned HTTP " + response.statusCode());
			}
			return response.body();
		} catch (Exception e) {
			throw new IllegalStateException("Instagram request failed", e);
		}
	}

	// --- response parsing ----------------------------------------------------

	private ReelPage parseFeed(String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		JsonObject data = root.getAsJsonObject("data");
		JsonObject connection = data == null ? null : data.getAsJsonObject(CONNECTION);
		if (connection == null) {
			throw new IllegalStateException("Unexpected feed response shape");
		}

		List<Reel> reels = new ArrayList<>();
		JsonArray edges = connection.getAsJsonArray("edges");
		if (edges != null) {
			for (JsonElement edge : edges) {
				JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
				JsonObject media = node == null ? null : node.getAsJsonObject("media");
				if (media != null) {
					Reel reel = toReel(media);
					reels.add(reel);
					String token = asString(media.get("organic_tracking_token"));
					if (token != null) {
						trackingTokens.put(reel.id(), token);
					}
					String code = asString(media.get("code"));
					if (code != null) {
						reelCodes.put(reel.id(), code);
					}
				}
			}
		}
		reels.forEach(r -> seenReels.add(r.id()));

		JsonObject pageInfo = connection.getAsJsonObject("page_info");
		boolean hasNext = pageInfo != null && pageInfo.has("has_next_page") && pageInfo.get("has_next_page").getAsBoolean();
		String nextCursor = hasNext ? asString(pageInfo.get("end_cursor")) : null;
		return new ReelPage(reels, nextCursor);
	}

	private boolean parseLikedState(String body, boolean requestedState) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		JsonObject data = root.getAsJsonObject("data");
		String key = requestedState ? "xig_media_like" : "xig_media_unlike";
		JsonObject mutation = data == null ? null : data.getAsJsonObject(key);
		JsonObject media = mutation == null ? null : mutation.getAsJsonObject("media");
		if (media != null && media.has("has_liked") && !media.get("has_liked").isJsonNull()) {
			return media.get("has_liked").getAsBoolean();
		}
		throw new IllegalStateException("Unexpected Instagram like response shape");
	}

	private Reel toReel(JsonObject media) {
		JsonObject user = media.getAsJsonObject("user");
		String id = asString(media.get("pk"));
		if (id == null) {
			id = orEmpty(asString(media.get("id")));
		}
		String author = user != null ? asString(user.get("username")) : null;

		JsonObject caption = media.getAsJsonObject("caption");
		String captionText = caption != null ? asString(caption.get("text")) : null;

		String thumbnail = null;
		JsonObject imageVersions = media.getAsJsonObject("image_versions2");
		if (imageVersions != null) {
			JsonArray candidates = imageVersions.getAsJsonArray("candidates");
			if (candidates != null && !candidates.isEmpty()) {
				thumbnail = asString(candidates.get(0).getAsJsonObject().get("url"));
			}
		}

		String video = null;
		JsonArray videoVersions = media.getAsJsonArray("video_versions");
		if (videoVersions != null && !videoVersions.isEmpty()) {
			video = asString(videoVersions.get(0).getAsJsonObject().get("url"));
		}

		long likeCount = media.has("like_count") && !media.get("like_count").isJsonNull()
				? media.get("like_count").getAsLong() : 0L;
		boolean liked = media.has("has_liked") && !media.get("has_liked").isJsonNull()
				&& media.get("has_liked").getAsBoolean();

		return new Reel(id, author != null ? author : "unknown", orEmpty(captionText), likeCount, thumbnail, video, liked);
	}

	// --- helpers -------------------------------------------------------------

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** Meta's jazoest checksum: "2" followed by the sum of fb_dtsg char codes. */
	private static String jazoest(String dtsg) {
		int sum = 0;
		for (int i = 0; i < dtsg.length(); i++) {
			sum += dtsg.charAt(i);
		}
		return "2" + sum;
	}

	private static String firstGroup(Pattern pattern, String input) {
		Matcher m = pattern.matcher(input);
		return m.find() ? m.group(1) : null;
	}

	private static String asString(JsonElement element) {
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}
}
