package gamlor.rochla;

import org.eclipse.jetty.websocket.api.CloseStatus;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.glyptodon.guacamole.GuacamoleClientException;
import org.glyptodon.guacamole.GuacamoleConnectionClosedException;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.io.GuacamoleReader;
import org.glyptodon.guacamole.io.GuacamoleWriter;
import org.glyptodon.guacamole.net.GuacamoleTunnel;
import org.glyptodon.guacamole.protocol.GuacamoleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executor;


/**
 * Guac web socket, implemented in Jetty's API.
 * The JSR api had more indirections and factories and cluncky initialisation.
 * Therefore, just ported to the more streigth forward Jetty api
 */
public class GuacJettyWebSocket extends WebSocketAdapter {
    private static final int BUFFER_SIZE = 8 * 1024;

    private final Logger logger = LoggerFactory.getLogger(GuacJettyWebSocket.class);
    private volatile GuacamoleTunnel tunnel;
    private final Executor executor;
    private final String info;
    private final TunnelConnector connect;

    public GuacJettyWebSocket(TunnelConnector connect, Executor executor, String info) {
        this.connect = connect;
        this.executor = executor;
        this.info = info;
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        super.onWebSocketConnect(session);

        try {
            tunnel = connect.connect(session);
            if (tunnel == null) {
                closeConnection(session, GuacamoleStatus.RESOURCE_NOT_FOUND);
                return;
            }
        } catch (GuacamoleException e) {
            logger.error("Creation of WebSocket tunnel to guacd failed for {}. {}", info, e);
            closeConnection(session, e.getStatus());
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("Guac-Websocket-Handler-For-" + info);
                StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
                GuacamoleReader reader = tunnel.acquireReader();
                char[] readMessage;

                try {
                    try {
                        // Attempt to read
                        while ((readMessage = reader.read()) != null) {

                            // Buffer message
                            buffer.append(readMessage);

                            // Flush if we expect to wait or buffer is getting full
                            if (!reader.available() || buffer.length() >= BUFFER_SIZE) {
                                getRemote().sendString(buffer.toString());
                                buffer.setLength(0);
                            }

                        }

                        // No more data
                        closeConnection(session, GuacamoleStatus.SUCCESS);

                    }

                    // Catch any thrown guacamole exception and attempt
                    // to pass within the WebSocket connection, logging
                    // each error appropriately.
                    catch (GuacamoleClientException e) {
                        logger.info("WebSocket connection terminated for {}: {}", info, e);
                        closeConnection(session, e.getStatus());
                    } catch (GuacamoleConnectionClosedException e) {
                        logger.debug("Connection to guacd closed.", e);
                        closeConnection(session, GuacamoleStatus.SUCCESS);
                    } catch (GuacamoleException e) {
                        logger.error("Connection to guacd terminated abnormally {}: {}", info, e.getMessage());
                    }

                } catch (IOException e) {
                    logger.error("I/O error prevents further reads for {}, {}", info, e);
                }
            }
        });


    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);
        try {
            if (tunnel != null)
                tunnel.close();
        } catch (GuacamoleException e) {
            logger.debug("Unable to close WebSocket tunnel for {}. {}", info, e);
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        super.onWebSocketError(cause);
        logger.error("Web socket error {}: {}", info, cause);
    }

    @Override
    public void onWebSocketText(String message) {
        // Ignore inbound messages if there is no associated tunnel
        if (tunnel == null)
            return;

        GuacamoleWriter writer = tunnel.acquireWriter();

        try {
            // Write received message
            writer.write(message.toCharArray());
        } catch (GuacamoleConnectionClosedException e) {
            logger.debug("Connection to guacd closed {}: {}", info, e);
        } catch (GuacamoleException e) {
            logger.debug("WebSocket tunnel write failed {}", info, e);
        }

        tunnel.releaseWriter();
    }


    private void closeConnection(Session session, GuacamoleStatus guac_status) {
        session.close(new CloseStatus(
                guac_status.getWebSocketCode(),
                Integer.toString(guac_status.getGuacamoleStatusCode())));
        logger.info("Closing web socket with socket status {} guac status: {} for session {}",
                guac_status.getWebSocketCode(),
                guac_status.getGuacamoleStatusCode(),
                info);

    }

}
