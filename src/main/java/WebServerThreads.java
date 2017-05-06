import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Diego Urrutia Astorga <durrutia@ucn.cl>
 * @version 20170330131600
 */
public class WebServerThreads {

    /**
     * Logger de la clase
     */
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    /**
     * Puerto de escucha
     */
    private static final Integer PORT = 9000;

    /**
     * Inicio del programa.
     *
     * @param args
     */
    public static void main(String[] args) throws IOException {

        log.debug("Starting ..");

        final ExecutorService executor = Executors.newFixedThreadPool(8);

        // Servidor en el puerto PORT
        final ServerSocket serverSocket = new ServerSocket(PORT);

        // Ciclo para atender a los clientes
        while (true) {

            log.debug("Waiting for connection in port {} ..", PORT);

            // 1 socket por peticion
            final Socket socket = serverSocket.accept();

            // Al executor ..
            final Runnable runnable = new ProcessRequestRunnable(socket);
            executor.execute(runnable);

            log.debug("Connection from {} in port {}.", socket.getInetAddress(), socket.getPort());

        }
    }
}
