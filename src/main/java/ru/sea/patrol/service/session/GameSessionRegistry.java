package ru.sea.patrol.service.session;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher eventPublisher;
	private final Map<String, ActiveSessionEntry> activeSessions = new ConcurrentHashMap<>();
	private final Map<String, GraceSessionEntry> reconnectGraceSessions = new ConcurrentHashMap<>();

	public GameSessionRegistry(
			GameRoomProperties roomProperties,
			RoomRegistry roomRegistry,
			RoomCatalogWsService roomCatalogWsService,
			ApplicationEventPublisher eventPublisher
	) {
		this.reconnectGracePeriodMillis = roomProperties.reconnectGracePeriod().toMillis();
		this.scheduler = Executors.newSingleThreadScheduledExecutor(sessionThreadFactory());
		this.roomRegistry = roomRegistry;
		this.roomCatalogWsService = roomCatalogWsService;
		this.eventPublisher = eventPublisher;
	}

	public synchronized boolean isLoginAllowed(String username) {
		return !activeSessions.containsKey(username);
	}

	public ClaimResult claimSession(String username, String sessionId) {
		String retainedRoomId = null;
		ClaimResult claimResult;
		SessionBinding restoredBinding = SessionBinding.lobby();
		synchronized (this) {
			var activeEntry = activeSessions.get(username);
			if (activeEntry != null) {
				return activeEntry.sessionId().equals(sessionId)
						? ClaimResult.RECONNECTED_SESSION
						: ClaimResult.REJECTED_DUPLICATE;
			}

			var graceEntry = reconnectGraceSessions.remove(username);
			if (graceEntry != null) {
				cancel(graceEntry.expirationTask());
				retainedRoomId = graceEntry.binding().roomId();
				restoredBinding = graceEntry.binding();
			}

			activeSessions.put(username, ActiveSessionEntry.active(sessionId, restoredBinding));
			claimResult = graceEntry == null ? ClaimResult.NEW_SESSION : ClaimResult.RECONNECTED_SESSION;
		}
		cleanupRetainedRoomIfNeeded(retainedRoomId);
		return claimResult;
	}

	public boolean registerDisconnect(String username, String sessionId) {
		synchronized (this) {
			var activeEntry = activeSessions.get(username);
			if (activeEntry == null || !activeEntry.sessionId().equals(sessionId)) {
				return false;
			}

			activeSessions.remove(username);
			ScheduledFuture<?> expirationTask = scheduler.schedule(
					() -> expireGraceWindow(username, sessionId),
					reconnectGracePeriodMillis,
					TimeUnit.MILLISECONDS
			);
			reconnectGraceSessions.put(username, GraceSessionEntry.disconnected(sessionId, activeEntry.binding(), expirationTask));
			return true;
		}
	}

	public synchronized boolean hasTrackedSession(String username) {
		return activeSessions.containsKey(username) || reconnectGraceSessions.containsKey(username);
	}

	public synchronized boolean isInReconnectGrace(String username) {
		return reconnectGraceSessions.containsKey(username);
	}

	public synchronized boolean hasActiveLobbySession(String username) {
		var activeEntry = activeSessions.get(username);
		return activeEntry != null && activeEntry.binding().isLobby();
	}

	public synchronized String activeRoomId(String username) {
		var activeEntry = activeSessions.get(username);
		if (activeEntry == null) {
			return null;
		}
		return activeEntry.binding().roomId();
	}

	public synchronized boolean bindToRoom(String username, String roomId) {
		var activeEntry = activeSessions.get(username);
		if (activeEntry == null || !activeEntry.binding().isLobby()) {
			return false;
		}
		activeSessions.put(username, activeEntry.withBinding(SessionBinding.room(roomId)));
		return true;
	}

	public synchronized boolean hasReconnectGraceInRoom(String roomId) {
		return roomId != null && reconnectGraceSessions.values().stream()
				.anyMatch(entry -> roomId.equals(entry.binding().roomId()));
	}

	@PreDestroy
	public void shutdown() {
		scheduler.shutdownNow();
	}

	private void expireGraceWindow(String username, String sessionId) {
		SessionGraceExpiredEvent expirationEvent = null;
		synchronized (this) {
			var graceEntry = reconnectGraceSessions.get(username);
			if (graceEntry != null && graceEntry.sessionId().equals(sessionId)) {
				reconnectGraceSessions.remove(username);
				if (graceEntry.binding().roomId() != null) {
					expirationEvent = new SessionGraceExpiredEvent(username, graceEntry.binding().roomId());
				}
			}
		}
		if (expirationEvent != null) {
			eventPublisher.publishEvent(expirationEvent);
		}
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

	private record ActiveSessionEntry(String sessionId, SessionBinding binding) {
		private static ActiveSessionEntry active(String sessionId) {
			return new ActiveSessionEntry(sessionId, SessionBinding.lobby());
		}

		private static ActiveSessionEntry active(String sessionId, SessionBinding binding) {
			return new ActiveSessionEntry(sessionId, binding == null ? SessionBinding.lobby() : binding);
		}

		private ActiveSessionEntry withBinding(SessionBinding newBinding) {
			return new ActiveSessionEntry(sessionId, newBinding);
		}
	}

	private record GraceSessionEntry(String sessionId, SessionBinding binding, ScheduledFuture<?> expirationTask) {
		private static GraceSessionEntry disconnected(String sessionId, SessionBinding binding, ScheduledFuture<?> expirationTask) {
			return new GraceSessionEntry(sessionId, binding, expirationTask);
		}
	}
}
