package ru.sea.patrol.service.session;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomRegistry;

@Service
public class GameSessionRegistry {

	public static final String DUPLICATE_SESSION_ERROR_CODE = "SEAPATROL_DUPLICATE_SESSION";
	public static final String DUPLICATE_SESSION_MESSAGE = "Active game session already exists";

	private final long reconnectGracePeriodMillis;
	private final ScheduledExecutorService scheduler;
	private final RoomRegistry roomRegistry;
	private final RoomCatalogWsService roomCatalogWsService;
	private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

	public GameSessionRegistry(
			GameRoomProperties roomProperties,
			RoomRegistry roomRegistry,
			RoomCatalogWsService roomCatalogWsService
	) {
		this.reconnectGracePeriodMillis = roomProperties.reconnectGracePeriod().toMillis();
		this.scheduler = Executors.newSingleThreadScheduledExecutor(sessionThreadFactory());
		this.roomRegistry = roomRegistry;
		this.roomCatalogWsService = roomCatalogWsService;
	}

	public synchronized boolean isLoginAllowed(String username) {
		var existing = sessions.get(username);
		return existing == null || existing.state() == SessionState.DISCONNECTED_GRACE;
	}

	public synchronized ClaimResult claimSession(String username, String sessionId) {
		var existing = sessions.get(username);
		if (existing == null) {
			sessions.put(username, SessionEntry.active(sessionId));
			return ClaimResult.NEW_SESSION;
		}

		if (existing.state() == SessionState.ACTIVE) {
			return existing.sessionId().equals(sessionId)
					? ClaimResult.RECONNECTED_SESSION
					: ClaimResult.REJECTED_DUPLICATE;
		}

		String retainedRoomId = existing.binding().roomId();
		cancel(existing.expirationTask());
		sessions.put(username, SessionEntry.active(sessionId));
		cleanupRetainedRoomIfNeeded(retainedRoomId);
		return ClaimResult.RECONNECTED_SESSION;
	}

	public synchronized void registerDisconnect(String username, String sessionId) {
		var existing = sessions.get(username);
		if (existing == null || existing.state() != SessionState.ACTIVE || !existing.sessionId().equals(sessionId)) {
			return;
		}

		ScheduledFuture<?> expirationTask = scheduler.schedule(
				() -> expireGraceWindow(username, sessionId),
				reconnectGracePeriodMillis,
				TimeUnit.MILLISECONDS
		);
		sessions.put(username, existing.disconnected(expirationTask));
	}

	public synchronized boolean hasTrackedSession(String username) {
		return sessions.containsKey(username);
	}

	public synchronized boolean isInReconnectGrace(String username) {
		var existing = sessions.get(username);
		return existing != null && existing.state() == SessionState.DISCONNECTED_GRACE;
	}

	public synchronized boolean hasActiveLobbySession(String username) {
		var existing = sessions.get(username);
		return existing != null && existing.state() == SessionState.ACTIVE && existing.binding().isLobby();
	}

	public synchronized boolean bindToRoom(String username, String roomId) {
		var existing = sessions.get(username);
		if (existing == null || existing.state() != SessionState.ACTIVE || !existing.binding().isLobby()) {
			return false;
		}
		sessions.put(username, existing.withBinding(SessionBinding.room(roomId)));
		return true;
	}

	public synchronized boolean hasReconnectGraceInRoom(String roomId) {
		return roomId != null && sessions.values().stream()
				.anyMatch(entry -> entry.state() == SessionState.DISCONNECTED_GRACE && roomId.equals(entry.binding().roomId()));
	}

	@PreDestroy
	public void shutdown() {
		scheduler.shutdownNow();
	}

	private void expireGraceWindow(String username, String sessionId) {
		String retainedRoomId = null;
		synchronized (this) {
			var existing = sessions.get(username);
			if (existing != null
					&& existing.state() == SessionState.DISCONNECTED_GRACE
					&& existing.sessionId().equals(sessionId)) {
				retainedRoomId = existing.binding().roomId();
				sessions.remove(username);
			}
		}
		cleanupRetainedRoomIfNeeded(retainedRoomId);
	}

	private void cleanupRetainedRoomIfNeeded(String roomId) {
		if (roomId == null) {
			return;
		}
		if (hasReconnectGraceInRoom(roomId)) {
			return;
		}
		if (roomRegistry.removeRoomIfEmpty(roomId)) {
			roomCatalogWsService.publishRoomsUpdated();
		}
	}

	private static void cancel(ScheduledFuture<?> future) {
		if (future != null) {
			future.cancel(false);
		}
	}

	private static ThreadFactory sessionThreadFactory() {
		return runnable -> {
			Thread thread = new Thread(runnable, "sea-patrol-session-registry");
			thread.setDaemon(true);
			return thread;
		};
	}

	public enum ClaimResult {
		NEW_SESSION,
		RECONNECTED_SESSION,
		REJECTED_DUPLICATE
	}

	private enum SessionState {
		ACTIVE,
		DISCONNECTED_GRACE
	}

	private record SessionBinding(String roomId) {
		private static SessionBinding lobby() {
			return new SessionBinding(null);
		}

		private static SessionBinding room(String roomId) {
			return new SessionBinding(roomId);
		}

		private boolean isLobby() {
			return roomId == null;
		}
	}

	private record SessionEntry(
			String sessionId,
			SessionState state,
			SessionBinding binding,
			ScheduledFuture<?> expirationTask
	) {

		private static SessionEntry active(String sessionId) {
			return new SessionEntry(sessionId, SessionState.ACTIVE, SessionBinding.lobby(), null);
		}

		private SessionEntry disconnected(ScheduledFuture<?> future) {
			return new SessionEntry(sessionId, SessionState.DISCONNECTED_GRACE, binding, future);
		}

		private SessionEntry withBinding(SessionBinding newBinding) {
			return new SessionEntry(sessionId, state, newBinding, expirationTask);
		}
	}
}
