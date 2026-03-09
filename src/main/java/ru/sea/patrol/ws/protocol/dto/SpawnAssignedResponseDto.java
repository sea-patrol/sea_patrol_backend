package ru.sea.patrol.ws.protocol.dto;

public record SpawnAssignedResponseDto(
		String roomId,
		SpawnReason reason,
		double x,
		double z,
		double angle
) {
}
