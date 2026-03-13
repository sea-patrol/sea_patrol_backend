package ru.sea.patrol.service.game;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.sea.patrol.service.game.map.MapTemplate;
import ru.sea.patrol.service.game.map.MapTemplateRegistry;

@Service
public class RoomRegistry {

	private final GameRoomProperties roomProperties;
	private final ApplicationEventPublisher eventPublisher;
	private final MapTemplateRegistry mapTemplateRegistry;
	private final MapTemplate defaultMapTemplate;
	private final long emptyRoomIdleTimeoutMillis;
	private final ScheduledExecutorService scheduler;
	private final Map<String, RoomRegistryEntry> rooms = new ConcurrentHashMap<>();
	private final Map<String, ScheduledFuture<?>> emptyRoomCleanupTasks = new ConcurrentHashMap<>();

	@Autowired
	public RoomRegistry(
			GameRoomProperties roomProperties,
			ApplicationEventPublisher eventPublisher,
			MapTemplateRegistry mapTemplateRegistry
	) {
		this(roomProperties, eventPublisher, mapTemplateRegistry, mapTemplateRegistry.defaultMap());
	}

	public RoomRegistry(GameRoomProperties roomProperties, ApplicationEventPublisher eventPublisher) {
		this(roomProperties, eventPublisher, null, MapTemplate.mvpDefault());
	}

	public RoomRegistry(GameRoomProperties roomProperties) {
		this(roomProperties, event -> {
		}, null, MapTemplate.mvpDefault());
	}

	private RoomRegistry(
			GameRoomProperties roomProperties,
			ApplicationEventPublisher eventPublisher,
			MapTemplateRegistry mapTemplateRegistry,
			MapTemplate defaultMapTemplate
	) {
		this.roomProperties = roomProperties;
		this.eventPublisher = eventPublisher;
		this.mapTemplateRegistry = mapTemplateRegistry;
		this.defaultMapTemplate = defaultMapTemplate;
		this.emptyRoomIdleTimeoutMillis = roomProperties.emptyRoomIdleTimeout().toMillis();
		this.scheduler = Executors.newSingleThreadScheduledExecutor(roomRegistryThreadFactory());
	}

	public synchronized GameRoom getOrCreateRoom(String roomId) {
		RoomRegistryEntry entry = rooms.computeIfAbsent(roomId, this::createDefaultEntry);
		if (entry.room().isEmpty()) {
			scheduleEmptyRoomCleanupIfNeeded(roomId);
		} else {
			cancelEmptyRoomCleanup(roomId);
		}
		return entry.room();
	}

	public synchronized RoomRegistryEntry createRoom(String requestedName, MapTemplate mapTemplate) {
		MapTemplate resolvedMapTemplate = mapTemplate == null ? defaultMapTemplate : mapTemplate;
		String roomId = nextAvailableRoomId(requestedName);
		String roomName = requestedName == null || requestedName.isBlank()
				? toDefaultRoomName(roomId)
				: requestedName.trim();
		var entry = new RoomRegistryEntry(
				roomId,
				roomName,
				resolvedMapTemplate.id(),
				resolvedMapTemplate.name(),
				new GameRoom(
						roomId,
						roomName,
						resolvedMapTemplate,
						roomProperties.updatePeriod().toMillis(),
						roomProperties.windRotationSpeed()
				)
		);
		rooms.put(roomId, entry);
		scheduleEmptyRoomCleanupIfNeeded(roomId);
		return entry;
	}

	public synchronized RoomRegistryEntry createRoom(String requestedName, String mapId, String mapName) {
		return createRoom(requestedName, resolveMapTemplate(mapId, mapName));
	}

	public GameRoom findRoom(String roomId) {
		var entry = rooms.get(roomId);
		return entry == null ? null : entry.room();
	}

	public RoomRegistryEntry findEntry(String roomId) {
		return rooms.get(roomId);
	}

	public synchronized void scheduleEmptyRoomCleanupIfNeeded(String roomId) {
		var entry = rooms.get(roomId);
		if (entry == null) {
			cancelEmptyRoomCleanup(roomId);
			return;
		}
		if (!entry.room().isEmpty()) {
			cancelEmptyRoomCleanup(roomId);
			return;
		}
		if (emptyRoomCleanupTasks.containsKey(roomId)) {
			return;
		}
		ScheduledFuture<?> cleanupTask = scheduler.schedule(
				() -> expireEmptyRoom(roomId),
				emptyRoomIdleTimeoutMillis,
				TimeUnit.MILLISECONDS
		);
		emptyRoomCleanupTasks.put(roomId, cleanupTask);
	}

	public synchronized void cancelEmptyRoomCleanup(String roomId) {
		ScheduledFuture<?> cleanupTask = emptyRoomCleanupTasks.remove(roomId);
		if (cleanupTask != null) {
			cleanupTask.cancel(false);
		}
	}

	public synchronized boolean removeRoomIfEmpty(String roomId) {
		var entry = rooms.get(roomId);
		if (entry == null || !entry.room().isEmpty()) {
			return false;
		}

		cancelEmptyRoomCleanup(roomId);
		rooms.remove(roomId);
		entry.room().stop();
		return true;
	}

	public synchronized int roomCount() {
		return rooms.size();
	}

	public synchronized boolean hasRoom(String roomId) {
		return rooms.containsKey(roomId);
	}

	public synchronized List<RoomRegistryEntry> roomsSnapshot() {
		return List.copyOf(new ArrayList<>(rooms.values()));
	}

	@PreDestroy
	public void shutdown() {
		emptyRoomCleanupTasks.values().forEach(task -> task.cancel(false));
		emptyRoomCleanupTasks.clear();
		scheduler.shutdownNow();
	}

	private void expireEmptyRoom(String roomId) {
		boolean removed = false;
		synchronized (this) {
			emptyRoomCleanupTasks.remove(roomId);
			var entry = rooms.get(roomId);
			if (entry == null || !entry.room().isEmpty()) {
				return;
			}
			rooms.remove(roomId);
			entry.room().stop();
			removed = true;
		}
		if (removed) {
			eventPublisher.publishEvent(new EmptyRoomExpiredEvent(roomId));
		}
	}

	private RoomRegistryEntry createDefaultEntry(String roomId) {
		return new RoomRegistryEntry(
				roomId,
				roomId,
				defaultMapTemplate.id(),
				defaultMapTemplate.name(),
				new GameRoom(
						roomId,
						roomId,
						defaultMapTemplate,
						roomProperties.updatePeriod().toMillis(),
						roomProperties.windRotationSpeed()
				)
		);
	}

	private MapTemplate resolveMapTemplate(String mapId, String mapName) {
		if (mapId == null || mapId.isBlank()) {
			return defaultMapTemplate;
		}
		if (mapTemplateRegistry != null) {
			var resolved = mapTemplateRegistry.get(mapId);
			if (resolved.isPresent()) {
				return resolved.get();
			}
		}
		if (mapId.equals(defaultMapTemplate.id())) {
			return defaultMapTemplate;
		}
		return new MapTemplate(
				mapId,
				mapName == null || mapName.isBlank() ? mapId : mapName,
				defaultMapTemplate.region(),
				defaultMapTemplate.revision(),
				false,
				true,
				defaultMapTemplate.bounds(),
				defaultMapTemplate.spawnRules(),
				defaultMapTemplate.files(),
				defaultMapTemplate.presentation(),
				defaultMapTemplate.defaultWind(),
				defaultMapTemplate.colliders(),
				defaultMapTemplate.spawnPoints(),
				defaultMapTemplate.pointsOfInterest(),
				defaultMapTemplate.minimap()
		);
	}

	private String nextAvailableRoomId(String requestedName) {
		String baseId = requestedName == null || requestedName.isBlank()
				? nextGeneratedSandboxId()
				: slugify(requestedName);
		String candidate = baseId;
		int suffix = 2;
		while (rooms.containsKey(candidate)) {
			candidate = baseId + "-" + suffix;
			suffix++;
		}
		return candidate;
	}

	private String nextGeneratedSandboxId() {
		int maxIndex = 0;
		for (String roomId : rooms.keySet()) {
			if (roomId.startsWith("sandbox-")) {
				try {
					int index = Integer.parseInt(roomId.substring("sandbox-".length()));
					maxIndex = Math.max(maxIndex, index);
				} catch (NumberFormatException ignored) {
					// Ignore non-standard sandbox ids.
				}
			}
		}
		return "sandbox-" + (maxIndex + 1);
	}

	private static String toDefaultRoomName(String roomId) {
		if (roomId.startsWith("sandbox-")) {
			String suffix = roomId.substring("sandbox-".length());
			return "Sandbox " + suffix;
		}
		return roomId;
	}

	private static String slugify(String value) {
		String slug = value.trim().toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");
		return slug.isBlank() ? "sandbox" : slug;
	}

	private static ThreadFactory roomRegistryThreadFactory() {
		return runnable -> {
			Thread thread = new Thread(runnable, "sea-patrol-room-registry");
			thread.setDaemon(true);
			return thread;
		};
	}
}
