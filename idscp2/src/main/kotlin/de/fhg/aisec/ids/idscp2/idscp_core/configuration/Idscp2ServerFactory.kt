package de.fhg.aisec.ids.idscp2.idscp_core.configuration

import de.fhg.aisec.ids.idscp2.Idscp2EndpointListener
import de.fhg.aisec.ids.idscp2.drivers.interfaces.DapsDriver
import de.fhg.aisec.ids.idscp2.drivers.interfaces.SecureChannelDriver
import de.fhg.aisec.ids.idscp2.error.Idscp2Exception
import de.fhg.aisec.ids.idscp2.idscp_core.Idscp2Connection
import de.fhg.aisec.ids.idscp2.idscp_core.Idscp2ConnectionAdapter
import de.fhg.aisec.ids.idscp2.idscp_core.secure_channel.SecureChannel
import de.fhg.aisec.ids.idscp2.idscp_core.server.Idscp2Server
import de.fhg.aisec.ids.idscp2.idscp_core.server.ServerConnectionListener
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Idscp2ServerFactory class, provides IDSCP2 API to the User (Idscp2EndpointListener)
 *
 * @author Leon Beckmann (leon.beckmann@aisec.fraunhofer.de)
 */
class Idscp2ServerFactory<CC: Idscp2Connection>(
        private val connectionFactory: (SecureChannel, Idscp2Settings, DapsDriver) -> CC,
        private val endpointListener: Idscp2EndpointListener<CC>,
        private val settings: Idscp2Settings,
        private val dapsDriver: DapsDriver,
        private val secureChannelDriver: SecureChannelDriver<CC>
) : SecureChannelInitListener<CC> {
    /**
     * User API to create a new IDSCP2 Server that starts a Secure Server that listens to new
     * secure channels
     */
    @Throws(Idscp2Exception::class)
    fun listen(settings: Idscp2Settings): Idscp2Server<CC> {
        LOG.info("Starting new IDSCP2 server at port {}", settings.serverPort)
        val serverListenerPromise = CompletableFuture<ServerConnectionListener<CC>>()
        val secureServer = secureChannelDriver.listen(settings, this, serverListenerPromise)
        val server = Idscp2Server<CC>(secureServer)
        serverListenerPromise.complete(server)
        return server
    }

    /**
     * A callback implementation to receive a new established secure channel from an Secure client/server.
     *
     * If the secure channel is null, no secure channel was established and an error is provided
     * to the user (or the error is ignored, in server case).
     *
     * If the secure channel was established, a new FSM is created for this connection and the
     * IDSCP2 handshake is started. After a successful handshake, a new Idscp2Connection is
     * created and provided to the user (and the IDSCP2 server).
     */
    @Synchronized
    override fun onSecureChannel(secureChannel: SecureChannel,
                                 serverListenerPromise: CompletableFuture<ServerConnectionListener<CC>>) {
        LOG.trace("A new secure channel for an IDSCP2 connection was established")
        // Threads calling onMessage() will be blocked until all listeners have been registered, see below
        val newConnection = connectionFactory(secureChannel, settings, dapsDriver)
        // Complete the connection promise for the IDSCP server
        serverListenerPromise.thenAccept { serverListener: ServerConnectionListener<CC> ->
            serverListener.onConnectionCreated(newConnection)
            newConnection.addConnectionListener(object : Idscp2ConnectionAdapter() {
                override fun onClose() {
                    serverListener.onConnectionClose(newConnection)
                }
            })
        }
        endpointListener.onConnection(newConnection)
        // Listeners have been applied in onConnection() callback above, so we can safely unlock messaging now
        newConnection.unlockMessaging()
    }

    override fun onError(t: Throwable) {
        endpointListener.onError(t)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Idscp2ServerFactory::class.java)
    }
}