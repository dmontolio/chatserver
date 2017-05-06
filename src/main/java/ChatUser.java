/**
 * Created by Daniel on 23-04-2017.
 *
 * Contains user data:
 * name
 * ID
 * TODO: user options
 */
public class ChatUser {
    private int id;
    private String name;
    private String passHash;
    private String creationDate;
    private String avatar;

    public ChatUser(int _id, String _name, String passHash, String _creationDate, String _avatar) {
        id = _id;
        name = _name;
        this.passHash = passHash;
        creationDate = _creationDate;
        avatar = _avatar;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPassHash() {
        return passHash;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public String getAvatar() {
        return avatar;
    }
}
