package dev.pppp.model;

/**
 * Something browsers should see live. Services publish it as a Spring ApplicationEvent;
 * the app module relays it to every /ws/events socket. Types: CHAT_MESSAGE, CHAT_DELETED, CHAT_REACTION, CHAT_CLEARED,
 * GAME_STATE ({user, state}: that player's GameSummary), LOBBY_CHANGED, LINK_CHALLENGE, LINK_SESSION (see LinkService),
 * PROGRESS (a ScoreEvent), SCOREBOARD_CHANGED (the new List of ScoreRow).
 */
public record LiveEvent(String type, Object payload) {}
