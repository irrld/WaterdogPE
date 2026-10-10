/*
 * Copyright 2022 WaterdogTEAM
 * Licensed under the GNU General Public License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.waterdog.waterdogpe.network.protocol.handler.upstream;

import org.cloudburstmc.protocol.bedrock.packet.*;
import dev.waterdog.waterdogpe.event.defaults.PlayerResourcePackApplyEvent;
import dev.waterdog.waterdogpe.packs.PackManager;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Upstream handler handling proxy manager resource packs.
 */
public class ResourcePacksHandler extends AbstractUpstreamHandler {

    // The client buffers out-of-order chunks in memory, so at most this many wait there
    private static final int HOLD_WINDOW = 100;

    // Packs offered to this client by id_version
    private final Map<String, ResourcePackDataInfoPacket> offeredPacks = new HashMap<>();
    private final Map<String, BitSet> sentChunks = new HashMap<>();
    // The client writes chunks in order on one IO thread that also unzips finished packs, and only shows an
    // in-order chunk on its progress bar once written. The first chunk of every window goes last, so the rest
    // skip that queue as on BDS.
    private final Map<String, BitSet> heldChunks = new HashMap<>();

    public ResourcePacksHandler(ProxiedPlayer player) {
        super(player);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        PackManager packManager = this.player.getProxy().getPackManager();

        switch (packet.getStatus()) {
            case REFUSED:
                this.player.disconnect("disconnectionScreen.noReason");
                break;
            case SEND_PACKS:
                // All up front, as the client's progress bar only counts packs it has been offered
                for (String packIdVer : packet.getPackIds()) {
                    ResourcePackDataInfoPacket response = packManager.packInfoFromIdVer(packIdVer);
                    if (response == null) {
                        this.player.disconnect("disconnectionScreen.resourcePack");
                        break;
                    }
                    if (this.offeredPacks.putIfAbsent(packIdVer, response) == null) {
                        this.player.sendPacket(response);
                    }
                }
                break;
            case HAVE_ALL_PACKS:
                PlayerResourcePackApplyEvent event = new PlayerResourcePackApplyEvent(this.player, packManager.getStackPacket());
                this.player.getProxy().getEventManager().callEvent(event);
                this.player.getConnection().sendPacket(event.getStackPacket());
                break;
            case COMPLETED:
                if (!this.player.hasUpstreamBridge()) {
                    this.player.initialConnect(); // First connection
                }
                break;
        }

        return this.cancel();
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        if (!this.player.isConnected()) {
            return this.cancel();
        }

        String packIdVer = packet.getPackId() + "_" + packet.getPackVersion();
        ResourcePackDataInfoPacket info = this.offeredPacks.get(packIdVer);
        int index = packet.getChunkIndex();
        if (info == null || index < 0 || index >= info.getChunkCount()) {
            return this.cancel();
        }

        // Each chunk is sent once
        BitSet sent = this.sentChunks.computeIfAbsent(packIdVer, id -> new BitSet());
        BitSet held = this.heldChunks.computeIfAbsent(packIdVer, id -> new BitSet());
        if (sent.get(index) || held.get(index)) {
            return this.cancel();
        }
        int start = index - index % HOLD_WINDOW;
        int end = (int) Math.min(start + HOLD_WINDOW, info.getChunkCount());
        if (index == start && sent.nextClearBit(start + 1) < end) {
            held.set(index);
            return this.cancel();
        }

        if (this.sendChunk(packIdVer, sent, packet) && held.get(start) && sent.nextClearBit(start + 1) >= end) {
            held.clear(start);
            ResourcePackChunkRequestPacket first = new ResourcePackChunkRequestPacket();
            first.setPackId(packet.getPackId());
            first.setPackVersion(packet.getPackVersion());
            first.setChunkIndex(start);
            this.sendChunk(packIdVer, sent, first);
        }
        return this.cancel();
    }

    private boolean sendChunk(String packIdVer, BitSet sent, ResourcePackChunkRequestPacket request) {
        ResourcePackChunkDataPacket response = this.player.getProxy().getPackManager().packChunkDataPacket(packIdVer, request);
        if (response == null) {
            this.player.disconnect("Unknown resource pack!");
            return false;
        }
        sent.set(request.getChunkIndex());
        // A batch of its own, as the client asks for many chunks at once and queued ones would share a batch
        this.player.sendPacketImmediately(response);
        return true;
    }
}
