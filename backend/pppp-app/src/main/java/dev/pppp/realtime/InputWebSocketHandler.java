package dev.pppp.realtime;

import dev.pppp.emulator.Button;
import dev.pppp.service.PlayerConsoles;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * /ws/input: the signed-in player's button presses on their own console as they happen, one text message per
 * change: "down UP" or "up UP" (button names as in {@link Button}). A held key stays held on the Game Boy, so
 * walking is smooth. Closing the socket lets go of every button. Anything else is ignored. Same security as
 * /ws/screen.
 */
@Component
public class InputWebSocketHandler extends TextWebSocketHandler {
    private final PlayerConsoles consoles;

    InputWebSocketHandler(PlayerConsoles consoles) {
        this.consoles = consoles;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String[] parts = message.getPayload().trim().split(" ");
        if (parts.length != 2 || !(parts[0].equals("down") || parts[0].equals("up"))) return;
        Button button;
        try {
            button = Button.valueOf(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        consoles.hold(ScreenWebSocketHandler.userId(session), button, parts[0].equals("down"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        consoles.releaseAll(ScreenWebSocketHandler.userId(session));
    }
}
