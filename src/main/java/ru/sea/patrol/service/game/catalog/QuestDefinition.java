package ru.sea.patrol.service.game.catalog;

public record QuestDefinition(
		String id,
		String name,
		QuestKind kind,
		QuestObjectiveType objectiveType,
		String targetId,
		int targetCount,
		int rewardGold
) {

	public QuestDefinition {
		id = normalizeRequired(id, "Quest id");
		name = normalizeRequired(name, "Quest name");
		if (kind == null) {
			throw new IllegalArgumentException("Quest kind must not be null");
		}
		if (objectiveType == null) {
			throw new IllegalArgumentException("Quest objectiveType must not be null");
		}
		targetId = normalizeRequired(targetId, "Quest targetId");
		if (targetCount <= 0) {
			throw new IllegalArgumentException("Quest targetCount must be greater than zero");
		}
		if (rewardGold < 0) {
			throw new IllegalArgumentException("Quest rewardGold must be zero or positive");
		}
	}

	private static String normalizeRequired(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}
}
