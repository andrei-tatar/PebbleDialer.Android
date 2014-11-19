package atatar.com.pebbledialer;

interface IDialerService {
    String[] getMessages();
    void appendMessage(String message);
    void insertMessage(int position, String message);
    void updateMessage(int position, String message);
    void removeMessage(int position);

    Contact[] getContacts();
    void appendContact(Contact contact);
    void insertContact(int position, Contact contact);
    void removeContact(int position);

    void setEnabled(boolean enabled);
    boolean getEnabled();

    boolean getNotifyCalls();
    boolean setNotifyCalls(boolean notifyCalls);

    boolean getRemoveAccents();
    void setRemoveAccents(boolean removeAccents);
}
