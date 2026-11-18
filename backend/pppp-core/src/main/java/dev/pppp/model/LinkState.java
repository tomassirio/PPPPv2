package dev.pppp.model;

import java.util.List;

/**
 * What one player sees of the link cable: the lobby (everyone ready, themselves included), what they are ready
 * for, the pending challenge to and from them, and their running session. Times are epoch milliseconds (since: when the cable went in); now is
 * the server's clock, so a browser can count down without trusting its own. links: every running session, one entry
 * per pair (who is linked is public, like the lobby).
 */
public record LinkState(List<Ready> lobby, String ready, Challenge incoming, Challenge outgoing, Session session,
                        List<Pair> links, long now) {
    /** kind: TRADE or BATTLE. */
    public record Ready(String userId, String kind) {}

    /** Also the LINK_CHALLENGE payload. status: PENDING, ACCEPTED, DECLINED, EXPIRED or CANCELLED. */
    public record Challenge(String from, String to, String kind, long expiresAt, String status) {}

    /**
     * wrongRoom, peerWrongRoom: the link room (TRADE_CENTER, COLOSSEUM, TIME_CAPSULE) this player or their partner
     * walked into that does not fit kind; null while they are not in one.
     */
    public record Session(String peer, String kind, long since, String wrongRoom, String peerWrongRoom) {}
    public record Pair(String a, String b, String kind, long since) {}

    /**
     * LINK_SESSION payload. status: STARTED, ENDED, or ROOM (a player of the pair walked into a link room that does
     * not fit kind, or out of it: fetch the state again); reason (ENDED only): UNPLUGGED, LEFT or FINISHED (back
     * out of the link room after the battle or trade).
     */
    public record SessionEvent(String a, String b, String kind, long since, String status, String reason) {}
}
