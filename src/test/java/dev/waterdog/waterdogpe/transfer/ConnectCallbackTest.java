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

package dev.waterdog.waterdogpe.transfer;

import dev.waterdog.waterdogpe.event.defaults.ServerPreConnectEvent;
import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.handler.ReconnectReason;
import dev.waterdog.waterdogpe.network.protocol.handler.TransferCallback;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import io.netty.util.concurrent.Promise;
import net.craftersmc.ConnectCallback;
import net.craftersmc.ConnectState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ConnectCallbackTest {

    private TransferTestHarness harness;
    private ServerInfo lobbyServer;
    private ClientConnection lobbyConnection;

    private static final class RecordingCallback extends ConnectCallback {
        final List<ConnectState> states = new ArrayList<>();
        ServerInfo lastTarget;
        Throwable lastError;

        @Override
        public void whenComplete(ConnectState state, ServerInfo targetServer, Throwable error) {
            this.states.add(state);
            this.lastTarget = targetServer;
            this.lastError = error;
        }
    }

    @BeforeEach
    void setUp() {
        this.harness = new TransferTestHarness();
        this.lobbyServer = this.harness.newServer("lobby");
        this.lobbyConnection = this.harness.newDownstream(this.lobbyServer);
        this.harness.setActiveDownstream(this.lobbyConnection);
    }

    @AfterEach
    void tearDown() {
        this.harness.close();
    }

    private RecordingCallback connectTo(ServerInfo target, Promise<ClientConnection> dial, ClientConnection connection) {
        RecordingCallback callback = new RecordingCallback();
        this.harness.player.connect(target, callback);
        if (connection != null) {
            dial.setSuccess(connection);
        }
        return callback;
    }

    @Test
    void throwingCallbackIsContained() {
        ConnectCallback callback = new ConnectCallback() {
            @Override
            public void whenComplete(ConnectState state, ServerInfo targetServer, Throwable error) {
                throw new RuntimeException("plugin bug");
            }
        };
        assertDoesNotThrow(() -> callback.completeWith(ConnectState.CONNECTED, this.lobbyServer, null));
    }

    @Test
    void throwingCallbackDoesNotBreakPromotion() {
        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        ClientConnection connection = this.harness.newDownstream(target);
        this.harness.player.connect(target, new ConnectCallback() {
            @Override
            public void whenComplete(ConnectState state, ServerInfo targetServer, Throwable error) {
                throw new RuntimeException("plugin bug");
            }
        });
        dial.setSuccess(connection);

        assertDoesNotThrow(() -> this.harness.player.setDownstreamConnection(connection));
        assertSame(connection, this.harness.player.getDownstreamConnection());
    }

    @Test
    void completesAtMostOnce() {
        RecordingCallback callback = new RecordingCallback();
        callback.completeWith(ConnectState.CONNECTED, this.lobbyServer, null);
        callback.completeWith(ConnectState.FAILED, this.lobbyServer, new Throwable("late"));
        assertEquals(List.of(ConnectState.CONNECTED), callback.states);
    }

    @Test
    void completesConnectedWhenStartGamePromotes() {
        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        ClientConnection connection = this.harness.newDownstream(target);

        RecordingCallback callback = connectTo(target, dial, connection);
        assertTrue(callback.states.isEmpty(), "connection established is not yet a terminal state");

        this.harness.player.setDownstreamConnection(connection);
        assertEquals(List.of(ConnectState.CONNECTED), callback.states);
        assertSame(target, callback.lastTarget);

        // A late watchdog tick must not add another completion.
        this.harness.runScheduledTasks();
        assertEquals(1, callback.states.size());
    }

    @Test
    void completesRecoveredWhenPendingConnectionFails() {
        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        ClientConnection connection = this.harness.newDownstream(target);
        RecordingCallback callback = connectTo(target, dial, connection);

        this.harness.player.onTransferFailure(connection, target, ReconnectReason.SERVER_KICK, "kicked");

        assertEquals(List.of(ConnectState.RECOVERED), callback.states);
        assertSame(this.lobbyConnection, this.harness.player.getDownstreamConnection());
        assertTrue(this.harness.player.isConnected());
    }

    @Test
    void completesRecoveredWhenWatchdogFires() {
        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        ClientConnection connection = this.harness.newDownstream(target);
        RecordingCallback callback = connectTo(target, dial, connection);

        this.harness.runScheduledTasks();

        assertEquals(List.of(ConnectState.RECOVERED), callback.states);
    }

    @Test
    void completesRecoveredWhenDialFailsWithHealthyServer() {
        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        RecordingCallback callback = new RecordingCallback();

        this.harness.player.connect(target, callback);
        dial.setFailure(new Exception("boom"));

        assertEquals(List.of(ConnectState.RECOVERED), callback.states);
        assertTrue(this.harness.player.isConnected());
    }

    @Test
    void completesFallbackWithoutHealthyServer() {
        TransferTestHarness.setField(this.harness.player, "clientConnection", null);
        ServerInfo fallback = this.harness.newServer("fallback");
        this.harness.stubDial(fallback);
        when(this.harness.reconnectHandler.getFallbackServer(any(), any(), any(), anyString())).thenReturn(fallback);

        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        RecordingCallback callback = new RecordingCallback();
        this.harness.player.connect(target, callback);
        dial.setFailure(new Exception("boom"));

        assertEquals(List.of(ConnectState.FALLBACK), callback.states);
        assertTrue(this.harness.player.isConnected());
    }

    @Test
    void completesFailedWhenNothingIsLeft() {
        TransferTestHarness.setField(this.harness.player, "clientConnection", null);

        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        RecordingCallback callback = new RecordingCallback();
        this.harness.player.connect(target, callback);
        dial.setFailure(new Exception("boom"));

        assertEquals(List.of(ConnectState.FAILED), callback.states);
        assertNotNull(callback.lastError);
        assertEquals("boom", callback.lastError.getMessage());
        assertFalse(this.harness.player.isConnected());
    }

    @Test
    void completesAlreadyConnectingWhileTransferActive() {
        ClientConnection transferTarget = this.harness.newDownstream(this.harness.newServer("game"));
        TransferCallback active = new TransferCallback(this.harness.player, transferTarget, this.lobbyServer, 0);
        assertTrue(this.harness.player.getRewriteData().trySetTransferCallback(active));

        ServerInfo other = this.harness.newServer("other");
        RecordingCallback callback = new RecordingCallback();
        this.harness.player.connect(other, callback);

        assertEquals(List.of(ConnectState.ALREADY_CONNECTING), callback.states);
    }

    @Test
    void completesCancelledWhenSuperseded() {
        ServerInfo first = this.harness.newServer("first");
        Promise<ClientConnection> firstDial = this.harness.stubDial(first);
        ClientConnection firstConnection = this.harness.newDownstream(first);
        RecordingCallback firstCallback = connectTo(first, firstDial, firstConnection);

        ServerInfo second = this.harness.newServer("second");
        this.harness.stubDial(second);
        this.harness.player.connect(second, new RecordingCallback());

        assertEquals(List.of(ConnectState.CANCELLED), firstCallback.states, "superseded attempt should be cancelled");
    }

    @Test
    void completesCancelledWhenPreConnectEventIsCancelled() {
        this.harness.eventInterceptor = event -> {
            if (event instanceof ServerPreConnectEvent preConnectEvent) {
                preConnectEvent.setCancelled(true);
            }
        };

        ServerInfo target = this.harness.newServer("game");
        this.harness.stubDial(target);
        RecordingCallback callback = new RecordingCallback();
        this.harness.player.connect(target, callback);

        assertEquals(List.of(ConnectState.CANCELLED), callback.states);
    }

    @Test
    void completesDisconnectedWhenPlayerQuits() {
        ServerInfo target = this.harness.newServer("game");
        Promise<ClientConnection> dial = this.harness.stubDial(target);
        ClientConnection connection = this.harness.newDownstream(target);
        RecordingCallback callback = connectTo(target, dial, connection);

        this.harness.player.disconnect("quit");

        assertEquals(List.of(ConnectState.DISCONNECTED), callback.states);
    }
}