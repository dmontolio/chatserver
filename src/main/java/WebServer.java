import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * @author Diego Urrutia Astorga <durrutia@ucn.cl>
 * @version 20170316115400
 */
public final class WebServer {

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

        // Servidor en el puerto PORT
        final ServerSocket serverSocket = new ServerSocket(PORT);

        // Ciclo para atender a los clientes
        while (true) {

            // Contador de lineas
            int n = 0;

            log.debug("Waiting for connection ..");

            // 1 socket por peticion
            final Socket socket = serverSocket.accept();

            log.debug("Connection from {} in port {}.", socket.getInetAddress(), socket.getPort());

            // Request
            String request = null;

            // Iterador de la peticion
            final LineIterator lineIterator = IOUtils.lineIterator(socket.getInputStream(), Charset.defaultCharset());

            // Output
            final OutputStream outputStream = IOUtils.buffer(socket.getOutputStream());

            while (lineIterator.hasNext()) {

                // Leo linea a linea
                final String line = lineIterator.nextLine();
                log.debug("Line {}: {}", ++n, line);

                // Guardo la peticion de la primera linea
                if (n == 1) {
                    request = line;
                }

                // Termine la peticion si llegue al final de la peticion
                if (StringUtils.isEmpty(line)) {
                    break;
                }

            }

            log.debug("Request: {}", request);

            // Si llego la orden de shutdown ..
            if (StringUtils.contains(request, "shutdown")) {

                // .. Cierro el servicio
                socket.close();
                serverSocket.close();
                break;

            }

            // Caso particular ..
            if (StringUtils.contains(request, "favicon.ico")) {
                IOUtils.write("HTTP/1.1 404 Not Found\r\n\r\n", outputStream, Charset.defaultCharset());
                IOUtils.closeQuietly(outputStream);
                continue;
            }

            // Proceso el request
            processRequest(outputStream, request);

        }

        // log.debug("End.");

    }

    /**
     * Procesa el request.
     *
     * @param outputStream
     * @param request
     */
    private static void processRequest(final OutputStream outputStream, final String request) throws IOException {

        log.debug("Writing data for {}", request);

        // Header
        IOUtils.write("HTTP/1.0 200 OK\r\n", outputStream, Charset.defaultCharset());
        IOUtils.write("Content-type: text/html\r\n", outputStream, Charset.defaultCharset());

        // end-header
        IOUtils.write("\r\n", outputStream, Charset.defaultCharset());

        // Body
        final String body = "<html><head><title>WebServer v1.0</title></head><body><h3>Result:</h3><pre>CONTENT</pre></body></html>";

        final String random = RandomStringUtils.randomAlphabetic(100);

        final String result = StringUtils.replace(body, "CONTENT", random);

        IOUtils.write(result + "\r\n", outputStream, Charset.defaultCharset());

        IOUtils.closeQuietly(outputStream);

        log.debug("End.");

    }


}
