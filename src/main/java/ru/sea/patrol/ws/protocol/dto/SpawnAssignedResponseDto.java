package ru.sea.patrol.ws.protocol.dto;

public record SpawnAssignedResponseDto(
		String roomId,
		String reason,
		double x,
		double z,
		double angle
) {
}
