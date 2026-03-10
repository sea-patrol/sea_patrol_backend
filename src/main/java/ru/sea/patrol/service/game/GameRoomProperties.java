package ru.sea.patrol.service.game;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "game.room")
public record GameRoomProperties(
		String defaultRoomName,
		Integer maxRooms,
		Integer maxPlayersPerRoom,
		Duration updatePeriod,
		Duration reconnectGracePeriod,
		Duration emptyRoomIdleTimeout
) {

	public GameRoomProperties {
		if (defaultRoomName == null || defaultRoomName.isBlank()) {
			throw new IllegalArgumentException("game.room.default-room-name must not be blank");
		}
		if (maxRooms == null || maxRooms <= 0) {
			throw new IllegalArgumentException("game.room.max-rooms must be greater than zero");
		}
		if (maxPlayersPerRoom == null || maxPlayersPerRoom <= 0) {
			throw new IllegalArgumentException("game.room.max-players-per-room must be greater than zero");
		}
		if (updatePeriod == null || updatePeriod.isZero() || updatePeriod.isNegative()) {
			throw new IllegalArgumentException("game.room.update-period must be greater than zero");
		}
		if (reconnectGracePeriod == null || reconnectGracePeriod.isZero() || reconnectGracePeriod.isNegative()) {
			throw new IllegalArgumentException("game.room.reconnect-grace-period must be greater than zero");
		}
		if (emptyRoomIdleTimeout == null || emptyRoomIdleTimeout.isZero() || emptyRoomIdleTimeout.isNegative()) {
			throw new IllegalArgumentException("game.room.empty-room-idle-timeout must be greater than zero");
		}
	}
}
