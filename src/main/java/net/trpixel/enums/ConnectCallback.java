package net.trpixel.enums;

import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import org.apache.logging.log4j.core.jmx.Server;

public abstract class ConnectCallback {
    public abstract void whenComplete(ConnectState callback, ServerInfo targetServer, Throwable error);
    public void completeWith(ConnectState callback, ServerInfo targetServer, Throwable error){
        whenComplete(callback, targetServer, error);
    }
}
