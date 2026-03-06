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

@Service
public class GameSessionRegistry {

	public static final String DUPLICATE_SESSION_ERROR_CODE = "SEAPATROL_DUPLICATE_SESSION";
	public static final String DUPLICATE_SESSION_MESSAGE = "Active game session already exists";

	private final long reconnectGracePeriodMillis;
	private final ScheduledExecutorService scheduler;
	private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

	public GameSessionRegistry(GameRoomProperties roomProperties) {
		this.reconnectGracePeriodMillis = roomProperties.reconnectGracePeriod().toMillis();
		this.scheduler = Executors.newSingleThreadScheduledExecutor(sessionThreadFactory());
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

		cancel(existing.expirationTask());
		sessions.put(username, SessionEntry.active(sessionId));
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

	@PreDestroy
	public void shutdown() {
		scheduler.shutdownNow();
	}

	private void expireGraceWindow(String username, String sessionId) {
		synchronized (this) {
			var existing = sessions.get(username);
			if (existing != null
					&& existing.state() == SessionState.DISCONNECTED_GRACE
					&& existing.sessionId().equals(sessionId)) {
				sessions.remove(username);
			}
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

	private record SessionEntry(String sessionId, SessionState state, ScheduledFuture<?> expirationTask) {

		private static SessionEntry active(String sessionId) {
			return new SessionEntry(sessionId, SessionState.ACTIVE, null);
		}

		private SessionEntry disconnected(ScheduledFuture<?> future) {
			return new SessionEntry(sessionId, SessionState.DISCONNECTED_GRACE, future);
		}
	}
}
