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

import dev.waterdog.waterdogpe.logger.MainLogger;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ConnectCallback {
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public abstract void whenComplete(ConnectState callback, ServerInfo targetServer, Throwable error);

    /**
     * Completes at most once: cascading failure paths may report the same attempt multiple times,
     * only the first terminal state reaches the callback. A throwing callback is contained so it
     * can not break the connect or failure handling that completed it.
     */
    public void completeWith(ConnectState callback, ServerInfo targetServer, Throwable error) {
        if (!this.completed.compareAndSet(false, true)) {
            return;
        }
        try {
            whenComplete(callback, targetServer, error);
        } catch (Throwable t) {
            MainLogger.getLogger().error("ConnectCallback handler threw while completing with " + callback, t);
        }
    }
}
