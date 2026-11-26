package dev.pppp.controller;

import dev.pppp.security.CurrentUser;
import dev.pppp.service.game.DailyRewards;
import dev.pppp.service.game.GroupDex;
import dev.pppp.service.game.WonderTrade;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** What the Pibs share: the daily rewards calendar, the group Pokédex, Wonder Trade. */
@RestController
public class CommunityController {
    /** slot: 0-5 in the party; species: what the browser saw there (409 when it changed). */
    public record Send(int slot, int species) {}

    private final DailyRewards rewards;
    private final GroupDex dex;
    private final WonderTrade wonder;

    public CommunityController(DailyRewards rewards, GroupDex dex, WonderTrade wonder) {
        this.rewards = rewards;
        this.dex = dex;
        this.wonder = wonder;
    }

    @GetMapping("/api/v1/me/days")
    public DailyRewards.Days days(Authentication auth) {
        return rewards.days(CurrentUser.id(auth));
    }

    @GetMapping("/api/v1/dex/group")
    public GroupDex.Dex groupDex() {
        return dex.dex();
    }

    @GetMapping("/api/v1/wonder-trade")
    public WonderTrade.Pool pool(Authentication auth) {
        return wonder.pool(CurrentUser.id(auth));
    }

    /** 409 when it cannot happen now (busy, linked, an egg, the last fighter, party changed, traded before). */
    @PostMapping("/api/v1/wonder-trade")
    public WonderTrade.Traded trade(@RequestBody Send req, Authentication auth) {
        return wonder.trade(CurrentUser.id(auth), req.slot(), req.species());
    }
}
