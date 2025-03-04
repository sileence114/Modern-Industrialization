/*
 * MIT License
 *
 * Copyright (c) 2020 Azercoco & Technici4n
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package aztech.modern_industrialization.compat.ftbteams;

import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.event.TeamEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class FTBTeamsFacadeImpl implements FTBTeamsFacade {
    // cached to save on needless allocations and overhead...
    private final Map<UUID, Iterable<UUID>> cachedPlayersInTeam = new HashMap<>();

    public FTBTeamsFacadeImpl() {
        // Reset cache if teams are modified
        TeamEvent.PLAYER_CHANGED.register(event -> cachedPlayersInTeam.clear());
        // Reset cache if the server stops
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> cachedPlayersInTeam.clear());
    }

    @Override
    public Iterable<UUID> getOtherPlayersInTeam(UUID playerUuid) {
        return cachedPlayersInTeam.computeIfAbsent(playerUuid, uuid -> {
            var team = FTBTeamsAPI.getManager().getPlayerTeam(uuid);
            if (team == null) {
                // Not supposed to happen, but just in case...
                return List.of();
            }
            var keys = new HashSet<>(team.getMembers());
            keys.remove(uuid);
            return keys;
        });
    }
}
