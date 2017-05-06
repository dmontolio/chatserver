import com.google.common.collect.Lists;

import java.util.List;

/**
 * Created by Roberto on 26-04-2017.
 */
public class ChatList {
    public static final int LOAD_CHAT_NUM = 40;

    private List<ChatMessage> messages;


    public ChatList() {
        //add latest messages
        DBManager dbm = new DBManager();
        dbm.setDBConnection();
        List<ChatMessage> lastMessages = dbm.getLastMessages(LOAD_CHAT_NUM);

        if (lastMessages.size() != 0)
            messages = lastMessages;
        else
            messages = Lists.newArrayList();

        dbm.closeDBConnection();
    }

    public void addNewMessage(ChatMessage message) {
        //Add message to DB
        DBManager dbm = new DBManager();
        dbm.setDBConnection();
        int result = dbm.insertChatMessage(message);

        if (result > 0)
            messages.add(message);
    }

    public List<ChatMessage> getNewMessages(int start) {
        int invertedIndex = getNewestID() - start;
        if (invertedIndex < 1 || invertedIndex > messages.size())
            return null;

        return messages.subList(messages.size() - invertedIndex, messages.size());
    }

    public List<ChatMessage> getOldMessages(int start) {
        if (start < 1 || start > getNewestID())
            return null;

        //If the oldest message loaded is greater than the user's, load more from DB
        if (getOldestID() > start) {
            DBManager dbm = new DBManager();
            dbm.setDBConnection();
            int limit = getOldestID() - start + LOAD_CHAT_NUM;
            List<ChatMessage> oldMessages = dbm.getOldMessages(limit, getOldestID());
            if (oldMessages != null)
                messages.addAll(0, oldMessages);
        }

        //Get position in array of the newest message to return
        int newPos = messages.size() - 1 - getNewestID() - start;

        //Get position in array of the oldest message to return
        int oldPos = newPos - LOAD_CHAT_NUM;
        if (oldPos < 0)
            oldPos = 0;

        return messages.subList(oldPos, newPos);
    }

    public int getNewestID() {
        if (messages.size() < 1)
            return -1;
        ChatMessage message = messages.get(messages.size()-1);

        return message.getId();
    }

    public int getOldestID() {
        if (messages.size() < 1)
            return -1;

        ChatMessage message = messages.get(0);
        return  message.getId();
    }
}
