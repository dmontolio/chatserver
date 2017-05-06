import com.google.common.base.Stopwatch;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Diego Urrutia Astorga <durrutia@ucn.cl>
 * @version 20170330130700
 */
public class ProcessRequestRunnable implements Runnable {
    /**
     * Constantes
     */
    private static final String REQUEST_HEADER = "rq_header";
    private static final String REQUEST_COOKIE = "Cookie: ";
    private static final String REQUEST_CONTENT_LENGTH = "Content-Length: ";

    private static final String POST_DATA_MESSAGE = "msgText=";
    private static final String POST_DATA_NAME = "userName=";
    private static final String POST_DATA_PASSWORD = "password=";
    private static final String POST_DATA_GRAVATAR = "gravatar=";

    private static final String COOKIE_ID = "id=";
    private static final String COOKIE_NEWEST_POST = "newPost=";
    private static final String COOKIE_OLDEST_POST = "oldPost=";
    private static final String COOKIE_LAST_USERS = "lastUsers=";

    /**
     * Logger de la clase
     */
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestRunnable.class);

    /**
     * Contenedor de Chat
     */
    private static final ChatList messages = new ChatList();

    /**
     * Contenedor de usuarios activos
     * Hash - nombre
     */
    private static final HashMap<String, ChatUser> users = new HashMap<>();

    /**
     *
     */
    private static int userListID = 0;

    /**
     * Socket asociado al cliente.
     */
    private Socket socket;

    /**
     * Datos de request header
     * Par nombre - valor
     */
    private HashMap<String, String> requestMap = new HashMap<>();
    private String postData = "";

    /**
     * Conexion con la BD
     */
    private DBManager dbManager = new DBManager();

    /**
     * Constructor
     *
     * @param socket Client socket
     */
    public ProcessRequestRunnable(final Socket socket) {
        this.socket = socket;
    }


    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {

        // Cronometro ..
        final Stopwatch stopWatch = Stopwatch.createStarted();

        log.debug("Connection from {} in port {}.", socket.getInetAddress(), socket.getPort());

        // A dormir!
        try {
            Thread.sleep(RandomUtils.nextInt(100, 500));
        } catch (InterruptedException e) {
            log.error("Error in sleeping", e);
        }

        try {
            getRequest();
            processRequest();
        } catch (Exception ex) {
            log.error("Error processing request", ex);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Nothing here
            }
        }

        log.debug("Request timex: {}.", stopWatch);

    }

    /**
     * Obtengo la linea de peticion de request
     */
    private void getRequest() {
        //String request = null;
        int n = 0;
        int contentLength;

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            do {
                // Leo linea a linea
                line = in.readLine();
                log.debug("Line {}: {}", ++n, line);

                // Termine la peticion si llegue al final de la peticion
                if (StringUtils.isEmpty(line)) {
                    break;
                }

                // Guardo la peticion de la primera linea
                if (n == 1) {
                    requestMap.put(REQUEST_HEADER, line);
                    continue;
                }

                String headerField = StringUtils.substringBefore(line, " ");
                requestMap.put(headerField + " ", StringUtils.substringAfter(line, " "));

            } while (!line.equals(""));

            String num = requestMap.get(REQUEST_CONTENT_LENGTH);
            if (num != null) {
                log.debug("getRequest, num != null");
                contentLength = Integer.parseInt(num);
                //Datos de POST
                if (contentLength > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < contentLength; i++)
                        sb.append((char) in.read());

                    postData = sb.toString();
                    log.debug("postData: {}", postData);
                }
            }

            //in.close();//tambien cierra el output stream
        } catch (Exception e) { log.error(e.toString());}
    }

    /**
     * Procesar peticion
     *
     */
    private void processRequest() throws IOException {

        // Peticion

        String request = requestMap.get(REQUEST_HEADER);
        log.debug("Request detected: {}", request);

        // Output
        final OutputStream outputStream = IOUtils.buffer(socket.getOutputStream());

        log.debug("Writing data for: {}", request);

        // Dividir el request
        final String[] request3 = StringUtils.split(request);

        // String from http protocol: "GET /chat HTTP/1.1"

        // Cada componente
        final String verbo = request3[0];
        final String uri = request3[1];
        final String version = request3[2];

        // HTTP header
        String location = writeHeader(outputStream, verbo, uri);

        // Deteccion de version
        if (!StringUtils.equals("HTTP/1.1", version)) {
            log.warn("Wrong version: {}", version);
        }

        // Revisar que recurso se esta pidiendo
        String resourceVars = StringUtils.substringAfter(location, "?");
        location = StringUtils.substringBefore(location, "?");
        switch (location)
        {
            case "/msg_ajax":
                addNewMessage(getPostData(postData, POST_DATA_MESSAGE),
                        getUserName());
                break;
            case "/msg_ajax_update": {
                String strNewestID = getCookieData(COOKIE_NEWEST_POST);
                if (strNewestID == null)
                    break;

                int newestID = Integer.parseInt(strNewestID);
                writeNewChatMessages(outputStream, newestID);
            }
                break;
            case "/msg_ajax_older": {
                String strOldestID = getCookieData(COOKIE_OLDEST_POST);
                if (strOldestID == null)
                    break;

                int oldestID = Integer.parseInt(strOldestID);
                writeOldChatMessages(outputStream, oldestID);
            }
                break;
            case "/users_ajax_update":
                writeUserList(outputStream);
                break;
            case "/chat":
                writeChat(outputStream, getUserName());
                break;
            case "/login":
            case "/logout":
                writeStaticContent(outputStream, "chat_login.html");
                break;
            case "/create_account":
                writeCreateAccount(outputStream, resourceVars);
                break;
            case "/js/jquery-3.2.1.js":
            case "/js/jquery.min.js":
            case "/js/bootstrap.js":
            case "/js/bootstrap.min.js":
            case "/js/npm.js":
            case "/css/bootstrap.css":
            case "/css/bootstrap.min.css":
            case "/css/bootstrap-theme.css":
            case "/css/bootstrap-theme.min.css":
                /*
            case "/fonts/glyphicons-halflings-regular.eot":
            case "/fonts/glyphicons-halflings-regular.svg":
            case "/fonts/glyphicons-halflings-regular.ttf":
            case "/fonts/glyphicons-halflings-regular.woff":
            case "/fonts/glyphicons-halflings-regular.woff2":
            */
            case "/chat4.html": {
                String name = location.substring(1, location.length());
                writeStaticContent(outputStream, name);
            }
            case "/default_avatar.png":
                writeBytes(outputStream, location.substring(1, location.length()));
                break;
            case "/test":
                writeStaticContent(outputStream, "test.html");
                break;
            case "/testajax":
                writeTestContent(outputStream);
                break;
            default:
                //404 page
                //writeBody(outputStream, request);
                break;
        }

        // Cierro el stream
        IOUtils.closeQuietly(outputStream);
        // Cierro la conexion con la BD
        dbManager.closeDBConnection();
    }

    /**
     * Escribe el encabezado del protocolo HTTP.
     *
     * @param outputStream
     * @throws IOException
     */
    private String writeHeader(final OutputStream outputStream, final String verbo, final String uri) throws IOException {
        String location = StringUtils.substringBefore(uri, "?");
        String response = "";
        String append = "";

        // Header
        if (verbo.equals("GET")) {
            switch (location) {
                case "/chat":
                    if (!isUserConnected(getCookieData(COOKIE_ID))) {
                        location = "/login";
                        response = createRedirectResponse(location);
                    }
                    break;
                case "/login":
                    if (isUserConnected(getCookieData(COOKIE_ID))) {
                        location = "/chat";
                        response = createRedirectResponse(location);
                    }
                    break;
                case "/msg_ajax_update": {
                    if (!isUserConnected(getCookieData(COOKIE_ID)))
                        break;

                    String strUserNewID = getCookieData(COOKIE_NEWEST_POST);
                    if (strUserNewID == null)
                        break;

                    int userNewID = Integer.parseInt(strUserNewID);

                    if (userNewID >= messages.getNewestID())
                        break;

                    append += createCookieHeader(COOKIE_NEWEST_POST, Integer.toString(messages.getNewestID()));
                    log.debug("messages.getNewestID(): {}", messages.getNewestID());
                }
                    break;
                case "/users_ajax_update": {
                    if (!isUserConnected(getCookieData(COOKIE_ID)))
                        break;

                    String strLastUsers = getCookieData(COOKIE_LAST_USERS);

                    if (strLastUsers == null)
                        break;

                    int lastUsers = Integer.parseInt(strLastUsers);

                    if (lastUsers == userListID)
                        break;

                    append += createCookieHeader(COOKIE_LAST_USERS, Integer.toString(userListID));
                    log.debug("userListID: {}", userListID);
                }
                    break;
                case "/testjax":
                    break;
            }

            String contentType = "text/html";
            if (response.isEmpty()) {
                String fileExtension = StringUtils.substringAfterLast(location, ".");

                if (!fileExtension.isEmpty()) {

                    switch (fileExtension) {
                        case "text/js":
                            contentType = "text/javascript";
                            break;
                        case "css":
                            contentType = "text/css";
                            break;
                        case "png":
                            contentType = "image/png";
                            break;
                    }

                    if (!contentType.equals("text/html")) {
                        URL url;
                        ByteSource bs;
                        try {
                            url = Resources.getResource(location.substring(1, location.length()));
                            bs = Resources.asByteSource(url);
                        } catch (IllegalArgumentException ex) {
                            log.error("Can't find file", ex);
                            response = createRedirectResponse("");
                            sendHeader(outputStream, response, "");
                            return "";
                        }

                        // Contenido
                        try {
                            //String content = IOUtils.toString(url, Charset.defaultCharset());
                            append += "content-length: " + bs.size() + "\r\n";

                        } catch (IOException ex) {
                            log.error("Error in read", ex);
                            return "";
                        }
                    }
                }

                response = "HTTP/1.0 200 OK\r\n";
                response += "Content-type: " + contentType + " \r\n";
            }

        } else if (verbo.equals("POST")) {
            log.debug("verb == POST");
            switch (location) {
                case "/create_account": {
                    log.debug("case create_account");
                    //No se enviaron datos para crear la cuenta
                    if (postData.isEmpty()) {
                        location += "?error=1";
                        break;
                    }

                    String newName = getPostData(postData, POST_DATA_NAME);
                    String newPassword = getPostData(postData, POST_DATA_PASSWORD);
                    String newGravatar = getPostData(postData, POST_DATA_GRAVATAR);

                    log.debug("newName: {}, newPassword: {}, newGravatar: {}", newName, newPassword, newGravatar);
                    //No se enviaron todos los datos
                    if (newName == null || newPassword == null) {
                        location += "/?error=1";
                        break;
                    }

                    //Buscar si existe la cuenta
                    ChatUser user = getUserData(newName);

                    //No existe la cuenta
                    if (user == null) {
                        //Insertar la cuenta
                        createNewAccount(newName, newPassword, newGravatar);
                        location = "/login";//mensaje de exito?
                    //Ya existe la cuenta
                    } else {
                        location += "?error=2";
                    }
                }
                    break;
                case "/login": {
                    //Revisar si tiene una sesion activa
                    String sessionID = getCookieData(COOKIE_ID);
                    //Si la tiene y el servidor la tiene guardada, mandarlo a chat
                    if (isUserConnected(sessionID)) {
                        location = "/chat";
                        break;
                    }

                    //Si no lo esta, comprobar que el usuario y contraseña existan en la db y esten correctos
                    String postName = getPostData(postData, POST_DATA_NAME);
                    String postPass = getPostData(postData, POST_DATA_PASSWORD);

                    log.debug("postName: {}, postPass: {}", postName, postPass);
                    //Revisar si puede loguear con los datos enviados
                    if (!isLoginCorrect(postName, postPass)) {
                        location = "/login?error=3";
                        break;
                    }

                    //Asignar una sesion al usuario
                    String newSessionID = createSessionID(postName);
                    //Obtener los datos del usuario
                    ChatUser user = getUserData(postName);
                    synchronized (users) {
                        users.put(newSessionID, user);
                    }

                    append = "Set-Cookie: " + COOKIE_ID + newSessionID + "\r\n";
                    synchronized (messages) {
                        int newMsgID = messages.getNewestID();
                        int oldMsgID = messages.getOldestID();

                        //No messages were loaded
                        if (newMsgID == -1)
                            break;

                        //Display LOAD_CHAT_NUM messages when client logs in
                        if (newMsgID - oldMsgID > ChatList.LOAD_CHAT_NUM)
                            oldMsgID = newMsgID - ChatList.LOAD_CHAT_NUM;

                        //Setting newest message ID as oldMsgID-1 makes the client to request
                        //all messages from oldMsgID inclusive
                        append += createCookieHeader(COOKIE_NEWEST_POST, Integer.toString(oldMsgID - 1));
                        append += createCookieHeader(COOKIE_OLDEST_POST, Integer.toString(oldMsgID));
                        append += createCookieHeader(COOKIE_LAST_USERS, Integer.toString(userListID));
                        userListID++;
                    }
                    location = "/chat";
                }
                    break;
                case "/logout": {
                    String sessionID = getCookieData(COOKIE_ID);
                    if (isUserConnected(sessionID)) {
                        synchronized (users) {
                            users.remove(sessionID);
                        }
                    }
                    append = "Set-Cookie: " + COOKIE_ID + "\r\n";
                    append += "Set-Cookie: " + COOKIE_NEWEST_POST + "\r\n";
                    append += "Set-Cookie: " + COOKIE_OLDEST_POST + "\r\n";

                    location = "/login";
                }
                    break;
                case "/chat":
                case "/chat2":
                case "/msg_ajax":
                    {
                        String sessionID = getCookieData(COOKIE_ID);
                        if (!isUserConnected(sessionID)) {
                            //El usuario no ha iniciado sesion
                            location = "/login?error=2";
                            break;
                        }

                        if (!location.equalsIgnoreCase("/msg_ajax"))
                            break;

                        response = "HTTP/1.0 200 OK\r\n";
                        response += "Content-type: text/html\r\n";

                        //int newMsgID = messages.getNewestID();
                        //String strNewUserMsgID = getCookieData(COOKIE_NEWEST_POST);

                        //if (strNewUserMsgID == null)
                            //break;

                        //int newUserMsgID = Integer.parseInt(getCookieData(COOKIE_NEWEST_POST));

                        //if (newMsgID > newUserMsgID)
                            //append += "Set-Cookie: " + COOKIE_NEWEST_POST + newMsgID + "\r\n";
                    }
                    break;
            }

            if (response.isEmpty())
                response = createRedirectResponse(location);
        }

        sendHeader(outputStream, response, append);
        return location;
    }

    private static void sendHeader(final OutputStream outputStream, final String response, final String append)
            throws IOException {
        IOUtils.write(response, outputStream, Charset.defaultCharset());

        if (!append.isEmpty())
            IOUtils.write(append, outputStream, Charset.defaultCharset());

        // end-header
        IOUtils.write("\r\n", outputStream, Charset.defaultCharset());
    }

    /**
     * @param outputStream
     */
    private static void writeChat(final OutputStream outputStream, final String name) throws IOException {
        // Contenido completo
        final String content = readTextFile("chat4.html");

        String contentChat = StringUtils.replace(content, "NAME", name);

        // Envio el contenido
        IOUtils.write(contentChat + "\r\n", outputStream, Charset.defaultCharset());
    }

    private static void writeNewChatMessages(final OutputStream outputStream, final int newUserMsgID)
            throws IOException {
        int newMsgID = messages.getNewestID();

        //No new messages
        if (newMsgID < newUserMsgID || newMsgID == -1)
            return;

        List<ChatMessage> tempList = messages.getNewMessages(newUserMsgID);
        if (tempList == null)
            return;

        // Listado completo de chat
        final StringBuffer sb = new StringBuffer();
        // Linea de chat
        final String chatline = readTextFile("chatline3.html");

        for (ChatMessage chatMessage : tempList) {
            //TODO: add avatar
            String temp = StringUtils.replace(chatline, "USER_NAME", chatMessage.getUsername());
            sb.append(StringUtils.replace(temp, "CONTENT", chatMessage.getMessage()));
            sb.append("\r\n");
        }
        // Envio el contenido
        IOUtils.write(sb.toString(), outputStream, Charset.defaultCharset());
    }

    private static void writeUserList(final OutputStream outputStream)
            throws IOException {
        // Listado completo de usuarios
        final StringBuffer sb = new StringBuffer();
        // Linea de chat
        final String userLine = readTextFile("userline2.html");

        for (Map.Entry<String, ChatUser> e : users.entrySet()) {
            ChatUser value = e.getValue();
            sb.append(StringUtils.replace(userLine, "USER_NAME", value.getName()));
            sb.append("\r\n");
        }

        // Envio el contenido
        IOUtils.write(sb.toString(), outputStream, Charset.defaultCharset());
    }

    private static void writeOldChatMessages(final OutputStream outputStream, final int oldUserMsgID)
            throws IOException {
        //No new messages
        if (oldUserMsgID < 1)
            return;

        List<ChatMessage> tempList = messages.getOldMessages(oldUserMsgID);

        if (tempList == null)
            return;

        // Listado completo de chat
        final StringBuffer sb = new StringBuffer();
        // Linea de chat
        final String chatline = readTextFile("chatline.html");

        for (ChatMessage chatMessage : tempList) {
            String temp = StringUtils.replace(chatline, "USER_NAME", chatMessage.getUsername());
            sb.append(StringUtils.replace(temp, "CONTENT", chatMessage.getMessage()));
            sb.append("\r\n");
        }
        // Envio el contenido
        IOUtils.write(sb.toString(), outputStream, Charset.defaultCharset());
    }

    private static void addNewMessage(final String message, final String name) throws IOException {
        if (message.isEmpty())
            return;

        //Decode msg
        String decoded = URLDecoder.decode(message, "UTF-8");
        log.debug("msg: {}", decoded);

        synchronized (messages) {
            ChatMessage chatMessage = new ChatMessage(name, decoded);
            messages.addNewMessage(chatMessage);
        }
    }

    private static void writeCreateAccount(final OutputStream outputStream, final String error) throws IOException {
        // Contenido completo
        String content = readTextFile("create_account.html");

        String errorString = "";
        if (!error.isEmpty()) {
            //Falta añadir estilo de error
            errorString = "EL NOMBRE DE USUARIO YA EXISTE";
        }
        content = StringUtils.replace(content, "<!-- ERROR1 -->", errorString);

        // Envio el contenido
        IOUtils.write(content + "\r\n", outputStream, Charset.defaultCharset());
    }

    private static void writeStaticContent(final OutputStream outputStream, final String fileName) throws IOException {
        final String staticPage = readTextFile(fileName);

        // Envio el contenido
        IOUtils.write(staticPage + "\r\n", outputStream, Charset.defaultCharset());
    }

    private static void writeBytes(final OutputStream outputStream, final String fileName) throws IOException {
        final byte[] bytes = readRawFile(fileName);

        // Envio el contenido
        IOUtils.write(bytes, outputStream);
    }

    private static String createRedirectResponse(final String location)
    {
        String response = "HTTP/1.0 303 See Other\r\n";
        response += "Location: http://localhost:9000" + location + "\r\n";
        return response;
    }

    private static String createSessionID(String name) {
        String hash;
        synchronized (users) {
            do {
                hash = createHash(name + Integer.toString(RandomUtils.nextInt()));

            } while (users.get(hash) != null);
        }

        return hash;
    }

    private static String createCookieHeader(String varName, String value) {
        return "Set-Cookie: " + varName + value + "\r\n";
    }

    /**
     * Escribe el body del encabezado.
     *
     * param outputStream
     * param request
     */
    /*
    private static void writeBody(OutputStream outputStream, String request) throws IOException {

        // Body
        final String body = "<html><head><title>WebServer v1.0</title></head><body><h3>Result:</h3><pre>CONTENT</pre></body></html>";

        final String random = RandomStringUtils.randomAlphabetic(100);

        final String result = StringUtils.replace(body, "CONTENT", random);

        IOUtils.write(result + "\r\n", outputStream, Charset.defaultCharset());
    }
*/
    private String getUserName()
    {
        String name = "";
        String id = getCookieData(COOKIE_ID);
        synchronized (users) {
            ChatUser chatUser = users.get(id);
            if (chatUser != null)
                name = chatUser.getName();
        }

        return name;
    }

    private static boolean isUserConnected(final String sessionID) {
        synchronized (users) {
            return sessionID != null && users.get(sessionID) != null;
        }
    }

    private boolean isLoginCorrect(String name, String password) {
        if (name == null || password == null)
            return false;

        ChatUser user = getUserData(name);
        if (user == null) {
            log.debug("user == null)");
            return false;
        }

        String passDB = user.getPassHash();
        String passHash = createPassHash(name, password, user.getCreationDate());

        int compare = StringUtils.compare(passDB, passHash);
        log.debug("\r\npassDB:   {}\r\npassHash: {}", passDB, passHash);

        return compare == 0;
    }

    private String getPostData(String source, String dataName)
    {
        return getData(source, dataName, "&");
    }

    private String getCookieData(String dataName)
    {
        return getData(requestMap.get(REQUEST_COOKIE), dataName, ";");

    }

    private static String getData(final String source, final String dataName, final String delimiter)
    {
        String data = StringUtils.substringAfter(source, dataName);
        data = StringUtils.substringBefore(data, delimiter);
        return data;
    }

    private ChatUser getUserData(final String name)
    {
        return dbManager.getUserData(name);
    }

    private int createNewAccount(String name, String password, String gravatar) {
        //Obtener la fecha de creacion de la cuenta
        Date date = new Date();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") ;
        String currentDateTime = format.format(date);

        String passwordHash = createPassHash(name, password, currentDateTime);

        return dbManager.insertNewAccount(name, passwordHash, currentDateTime, gravatar);
    }

    private static String createPassHash(final String name, final String password, final String creationDate) {
        return createHash(name+password+creationDate);
    }

    private static String createHash(final String base) {
        String passwordHash = "";

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(base.getBytes("UTF-8")); // Change this to "UTF-16" if needed

            byte[] digest = md.digest();
            passwordHash = String.format("%064x", new java.math.BigInteger(1, digest));
        } catch(Exception e) {
            log.error(e.toString());
        }

        return passwordHash;
    }

    /**
     * @param filename
     * @return the contenido del archivo.
     */
    private static String readTextFile(final String filename) {

        // URL del index
        URL url;
        try {
            url = Resources.getResource(filename);
        } catch (IllegalArgumentException ex) {
            log.error("Can't find file", ex);
            return null;
        }

        // Contenido
        try {
            return IOUtils.toString(url, Charset.defaultCharset());
        } catch (IOException ex) {
            log.error("Error in read", ex);
            return null;
        }
    }

    private static byte[] readRawFile(final String filename) {

        URL url;
        ByteSource bs;
        try {
            url = Resources.getResource(filename);
            bs = Resources.asByteSource(url);
        } catch (IllegalArgumentException ex) {
            log.error("Can't find file", ex);
            return null;
        }

        // Contenido
        try {
            return IOUtils.toByteArray(url);
        } catch (IOException ex) {
            log.error("Error in read", ex);
            return null;
        }
    }

    private void writeTestContent(OutputStream outputStream) throws IOException {
        //String cookie = requestMap.get(REQUEST_COOKIE);

        int r = RandomUtils.nextInt(0, 256);
        int g = RandomUtils.nextInt(0, 256);
        int b = RandomUtils.nextInt(0, 256);

        int height = RandomUtils.nextInt(360, 650);

        String data = "<div style='background:rgb(" + r + "," + g + "," + b + ");height:" + height + "px'></div>";
        // Envio el contenido
        int random = RandomUtils.nextInt(0,100);
        if (random > 50 )
            data = "";
        IOUtils.write(data, outputStream, Charset.defaultCharset());
    }
}
