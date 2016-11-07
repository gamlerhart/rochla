package gamlor.rochla;

import org.eclipse.jetty.websocket.api.Session;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.GuacamoleTunnel;

/**
 * Creates the tunnel. Only real purpose is to allow implementing this interface in Clojure,
 * so the gap between clojure and the {@link GuacJettyWebSocket} can be bridged
 */
public interface TunnelConnector {
    GuacamoleTunnel connect(Session session) throws GuacamoleException;
}
