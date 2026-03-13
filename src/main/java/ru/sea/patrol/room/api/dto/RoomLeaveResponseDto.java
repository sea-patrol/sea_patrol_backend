package ru.sea.patrol.room.api.dto;

public record RoomLeaveResponseDto(
		String roomId,
		String status,
		String nextState
) {
}
