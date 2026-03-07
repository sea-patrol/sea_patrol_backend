package ru.sea.patrol.service.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.GameRoomProperties;

class GameSessionRegistryTest {

	private static GameSessionRegistry newRegistry(Duration reconnectGracePeriod) {
		return new GameSessionRegistry(new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				reconnectGracePeriod
		));
	}

	@Test
	void claimSession_rejectsParallelActiveSession() {
		GameSessionRegistry registry = newRegistry(Duration.ofSeconds(30));

		assertThat(registry.claimSession("alice", "s1")).isEqualTo(GameSessionRegistry.ClaimResult.NEW_SESSION);
		assertThat(registry.isLoginAllowed("alice")).isFalse();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.REJECTED_DUPLICATE);
	}

	@Test
	void claimSession_allowsReconnectWithinGraceWindow() {
		GameSessionRegistry registry = newRegistry(Duration.ofSeconds(30));
		registry.claimSession("alice", "s1");
		registry.registerDisconnect("alice", "s1");

		assertThat(registry.isInReconnectGrace("alice")).isTrue();
		assertThat(registry.isLoginAllowed("alice")).isTrue();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);
		assertThat(registry.isInReconnectGrace("alice")).isFalse();
	}

	@Test
	void claimSession_startsInLobby_andCanBindToRoomOnce() {
		GameSessionRegistry registry = newRegistry(Duration.ofSeconds(30));
		registry.claimSession("alice", "s1");

		assertThat(registry.hasActiveLobbySession("alice")).isTrue();
		assertThat(registry.bindToRoom("alice", "sandbox-1")).isTrue();
		assertThat(registry.hasActiveLobbySession("alice")).isFalse();
		assertThat(registry.bindToRoom("alice", "sandbox-2")).isFalse();
	}

	@Test
	void disconnectGrace_expiresAndDropsTrackedSession() throws Exception {
		GameSessionRegistry registry = newRegistry(Duration.ofMillis(120));
		registry.claimSession("alice", "s1");
		registry.registerDisconnect("alice", "s1");

		Thread.sleep(250L);

		assertThat(registry.hasTrackedSession("alice")).isFalse();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.NEW_SESSION);
	}
}
