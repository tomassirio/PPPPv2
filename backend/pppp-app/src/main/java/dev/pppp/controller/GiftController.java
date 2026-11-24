package dev.pppp.controller;

import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.MysteryGifts;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admins send Mystery Gifts (MysteryGifts): to one player (to) or, with to null, everyone. */
@RestController
public class GiftController {
    public record Send(String gift, int quantity, String to) {}

    private final MysteryGifts gifts;

    public GiftController(MysteryGifts gifts) {
        this.gifts = gifts;
    }

    @GetMapping("/api/v1/admin/gifts")
    public List<MysteryGifts.Gift> catalog() {
        return MysteryGifts.CATALOG;
    }

    /** {sent: how many players it was queued for}. */
    @PostMapping("/api/v1/admin/gifts")
    public Map<String, Integer> send(@RequestBody Send req, Authentication auth) {
        String to = req.to() == null || req.to().isBlank() ? null : req.to().trim().toLowerCase();
        return Map.of("sent", gifts.send(CurrentUser.id(auth), req.gift(), req.quantity(), to));
    }
}
