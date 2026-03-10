package ru.sea.patrol.service.session;

public record SessionGraceExpiredEvent(String username, String roomId) {
}
