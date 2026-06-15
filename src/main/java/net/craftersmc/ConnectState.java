/*
 * Copyright 2026 WaterdogTEAM
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

package net.craftersmc;

public enum ConnectState {
    /**
     * The attempt failed and no working server was left: the player was kicked.
     */
    FAILED,
    /**
     * The attempt failed and the player was sent to a fallback server instead.
     */
    FALLBACK,
    /**
     * The target server accepted the player: StartGame was received and the transfer claimed the player.
     */
    CONNECTED,
    /**
     * The player disconnected from the proxy while the attempt was in flight.
     */
    DISCONNECTED,
    ALREADY_CONNECTED,
    ALREADY_CONNECTING,
    CANCELLED,
    /**
     * The attempt failed before StartGame but the player is safe: they stayed on the previous
     * server or the reconnect handler moved them. The proxy already messaged the player.
     */
    RECOVERED
}
