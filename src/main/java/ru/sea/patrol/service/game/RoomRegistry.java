package ru.sea.patrol.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RoomRegistry {

	private final GameRoomProperties roomProperties;
	private final Map<String, RoomRegistryEntry> rooms = new ConcurrentHashMap<>();

	public RoomRegistry(GameRoomProperties roomProperties) {
		this.roomProperties = roomProperties;
	}

	public synchronized GameRoom getOrCreateRoom(String roomId) {
		return rooms.computeIfAbsent(roomId, this::createDefaultEntry)
				.room();
	}

	public synchronized RoomRegistryEntry createRoom(String requestedName, String mapId, String mapName) {
		String roomId = nextAvailableRoomId(requestedName);
		String roomName = requestedName == null || requestedName.isBlank()
				? toDefaultRoomName(roomId)
				: requestedName.trim();
		var entry = new RoomRegistryEntry(
				roomId,
				roomName,
				mapId,
				mapName,
				new GameRoom(roomId, roomProperties.updatePeriod().toMillis())
		);
		rooms.put(roomId, entry);
		return entry;
	}

	public GameRoom findRoom(String roomId) {
		var entry = rooms.get(roomId);
		return entry == null ? null : entry.room();
	}

	public RoomRegistryEntry findEntry(String roomId) {
		return rooms.get(roomId);
	}

	public synchronized boolean removeRoomIfEmpty(String roomId) {
		var entry = rooms.get(roomId);
		if (entry == null || !entry.room().isEmpty()) {
			return false;
		}

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

	private RoomRegistryEntry createDefaultEntry(String roomId) {
		return new RoomRegistryEntry(
				roomId,
				roomId,
				RoomCatalogService.DEFAULT_MAP_ID,
				RoomCatalogService.DEFAULT_MAP_NAME,
				new GameRoom(roomId, roomProperties.updatePeriod().toMillis())
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
}
