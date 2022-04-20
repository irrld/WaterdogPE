/*
 * Copyright 2021 WaterdogTEAM
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

package net.trpixel.enums;

import com.nukkitx.protocol.bedrock.packet.ResourcePackDataInfoPacket;
import dev.waterdog.waterdogpe.event.defaults.PlayerEvent;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;

public class SendPackInfoEvent extends PlayerEvent {

    private ResourcePackDataInfoPacket packet;

    public SendPackInfoEvent(ProxiedPlayer player, ResourcePackDataInfoPacket packet) {
        super(player);
        this.packet = packet;
    }

    public ResourcePackDataInfoPacket getPacket() {
        return this.packet;
    }

    public void setPacket(ResourcePackDataInfoPacket packet) {
        this.packet = packet;
    }
}
