import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Daniel on 23-04-2017.

 * Contains a chat message. It has:
 * userName
 * message
 * post time of the message
 */

public class ChatMessage {
    private int id;
    private String userName;
    private String message;
    private String time;

    /**
     *
     * @param _userName user name
     * @param _message message
     */
    public ChatMessage(String _userName, String _message) {
        userName = _userName;
        message = _message;

        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") ;
        time = format.format(date);
    }

    public ChatMessage(int _id, String _userName, String _message, String _time) {
        id = _id;
        userName = _userName;
        message = _message;
        time = _time;
    }

    public String getUsername() {
        return userName;
    }

    public String getMessage() {
        return message;
    }

    public String getTime() {
        return time;
    }

    public int getId() { return id; }

    public void setUserName(String _userName) {
        userName = _userName;
    }

    public void setMessage(String _message) {
        userName = _message;
    }

    public void setTime(String _time) {
        userName = _time;
    }

    public void setId(int id) {
        this.id = id;
    }
}
