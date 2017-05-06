import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Created by Daniel on 23-04-2017.
 */
public class DBManager {
    private static final String DB_USER = "user";
    private static final String DB_USER_ID = "id";
    private static final String DB_USER_NAME = "name";
    private static final String DB_USER_PASSWORD = "password_hash";
    private static final String DB_USER_CREATION_DATE = "creation_date";
    private static final String DB_USER_AVATAR = "avatar";

    private static final String DB_MESSAGE = "chat_message";
    private static final String DB_MESSAGE_ID = "id";
    private static final String DB_MESSAGE_USER_ID = "user_id";
    private static final String DB_MESSAGE_MESSAGE = "message";
    private static final String DB_MESSAGE_TIME = "time";

    private static final String DB_MESSAGE_APP = "chat_view_app";

    /**
     * Logger de la clase
     */
    private static final Logger logger = LoggerFactory.getLogger(ProcessRequestRunnable.class);

    /**
     * Conexion con la BD
     */
    private Connection con;
    private Statement stmt;

    public DBManager() {
        con = null;
        stmt = null;
    }

    public ChatUser getUserData(String name)
    {
        ResultSet rs = executeQuerySelect("SELECT * FROM `" + DB_USER + "` WHERE `" + DB_USER_NAME +
                "`=\"" + name + "\";");
        ChatUser user = null;
        try {
            if (!rs.next())
                return null;

            user = new ChatUser(rs.getInt(DB_USER_ID), rs.getString(DB_USER_NAME), rs.getString(DB_USER_PASSWORD),
                    StringUtils.substringBefore(rs.getString(DB_USER_CREATION_DATE), "."),
                    rs.getString(DB_USER_AVATAR));
        } catch (Exception e) {
            logger.error(e.toString());
        }

        return user;
    }

    public List<ChatMessage> getLastMessages(int count) {
        String query = "SELECT MAX(`" + DB_MESSAGE_ID + "`) FROM `" + DB_MESSAGE + "`;";
        ResultSet rs = executeQuerySelect(query);
        int max = count;
        try {
            if (rs.next()) {
                max = rs.getInt(1);
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }

        query = "SELECT * from  `" + DB_MESSAGE_APP + "` WHERE `" + DB_MESSAGE_ID + "` > " + (max - count);
        query += " ORDER BY `" + DB_MESSAGE_ID + "` ASC LIMIT " + count +";";

        rs = executeQuerySelect(query);

        List<ChatMessage> messages = Lists.newArrayList();
        try {
            while (rs.next()) {
                ChatMessage msg = new ChatMessage(rs.getInt(DB_MESSAGE_ID), rs.getString(DB_USER_NAME),
                        rs.getString(DB_MESSAGE_MESSAGE), rs.getString(DB_MESSAGE_TIME));
                messages.add(msg);
                //add message
            }
        } catch (Exception e) {

        }

        return messages;
    }

    public List<ChatMessage> getOldMessages(int count, int start) {
        int lowLimit = start - count - 1;
        if (lowLimit < 0)
            lowLimit = 0;

        String query = "SELECT * from  `" + DB_MESSAGE_APP + "` WHERE `" + DB_MESSAGE_ID + "` < " + start;
        query += " AND `" + DB_MESSAGE_ID + "` > " + lowLimit;
        query += " ORDER BY `" + DB_MESSAGE_ID + "` ASC LIMIT " + count +";";

        ResultSet rs = executeQuerySelect(query);

        List<ChatMessage> messages = Lists.newArrayList();

        try {
            while (rs.next()) {
                ChatMessage msg = new ChatMessage(rs.getInt(DB_MESSAGE_ID), rs.getString(DB_USER_NAME),
                        rs.getString(DB_MESSAGE_MESSAGE), rs.getString(DB_MESSAGE_TIME));
                messages.add(msg);
            }
        } catch (Exception e) {

        }

        return messages;
    }

    public int insertChatMessage(ChatMessage message) {
        ChatUser user = getUserData(message.getUsername());
        if (user == null)
            return -1;

        String query = ("INSERT INTO `" + DB_MESSAGE + "` (`" + DB_MESSAGE_USER_ID + "`, `" + DB_MESSAGE_MESSAGE +
                "`, `" + DB_MESSAGE_TIME + "`) VALUES (" + user.getId() + ", \""+ message.getMessage() + "\", \"" +
                message.getTime() + "\");");

        logger.debug("query: {}", query);
        int result = executeQueryUpdate(query);

        //No pudo insertar el mensaje a la BD
        if (result == 0)
            return -1;

        //Obtener la id del mensaje
        query = "SELECT MAX(`" + DB_MESSAGE_ID + "`) FROM `" + DB_MESSAGE + "`;";

        logger.debug("query: {}", query);

        ResultSet rs = executeQuerySelect(query);


        int newID = -1;
        try {
            if (!rs.next()) {
                return -1;
            }

            newID = rs.getInt(1);
        } catch (Exception e) { }


        if (newID > 0)
            message.setId(newID);

        return newID;
    }

    public int insertNewAccount(String name, String passHash, String date, String gravatar) {
        String query = "INSERT INTO  `" + DB_USER + "`  (`" + DB_USER_NAME + "`,`" + DB_USER_PASSWORD + "`,`" +
                DB_USER_CREATION_DATE + "`";
        if (gravatar.isEmpty()) {
            query += ")";
        } else {
            query += ",`" + DB_USER_AVATAR + "`)";
        }

        query += " VALUES ('" + name + "', '" + passHash + "', '" + date + "'";

        if (gravatar.isEmpty()) {
            query += ");";
        } else {
            query += ",'" + gravatar + "');";
        }

        logger.debug("query: {}", query);
        return executeQueryUpdate(query);
    }

    public void setDBConnection() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/chat_db", "root", "root");
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }

    private ResultSet executeQuerySelect(final String query) {
        try {
            if (con == null)
                setDBConnection();

            if (con == null)
                return null;

            if (stmt == null)
                stmt = con.createStatement();

            return stmt.executeQuery(query);
        } catch (Exception e) {
            logger.error(e.toString());
        }

        return null;
    }

    private int executeQueryUpdate(final String query) {
        try {
            if (con == null)
                setDBConnection();

            if (con == null)
                return -1;

            if (stmt == null)
                stmt = con.createStatement();

            return stmt.executeUpdate(query);
        } catch (Exception e) {
            logger.error(e.toString());
        }

        return -1;
    }

    public void closeDBConnection() {
        //try { if (rs != null) rs.close(); } catch (Exception e) {logger.error(e.toString());};
        try { if (stmt != null) stmt.close(); } catch (Exception e) {
            logger.error(e.toString());}
        try { if (con != null) con.close(); } catch (Exception e) {
            logger.error(e.toString());}
    }
}
