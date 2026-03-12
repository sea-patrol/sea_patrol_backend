package ru.sea.patrol.service.game.catalog;

public enum QuestObjectiveType {
	DELIVER_ITEM,
	CATCH_ITEM,
	SINK_SHIP;

	public boolean requiresItemReference() {
		return this == DELIVER_ITEM || this == CATCH_ITEM;
	}
}
