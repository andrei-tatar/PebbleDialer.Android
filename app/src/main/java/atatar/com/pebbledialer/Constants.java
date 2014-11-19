package atatar.com.pebbledialer;

import java.util.UUID;

class Constants {
    static final byte KEY_TYPE = 0;
    static final byte TYPE_REQUEST_CONTACTS = 1;
    static final byte TYPE_REQUEST_DIAL = 2;
    static final byte TYPE_REQUEST_HANG_CALL = 3;
    static final byte TYPE_REQUEST_ANSWER_CALL = 4;
    static final byte TYPE_REQUEST_SILENCE = 5;
    static final byte TYPE_REQUEST_MESSAGES = (byte) 0xE5;
    static final byte TYPE_REQUEST_SEND_MESSAGE = 0x3E;
    static final byte TYPE_CONTACT = 1;
    static final byte TYPE_MESSAGE = 0x5E;


    static final byte KEY_PHONE_NUMBER = 1;

    static final byte KEY_CONTACT_INDEX = 1;
    static final byte KEY_CONTACT_NAME = 2;
    static final byte KEY_CONTACT_PHONE = 3;
    static final byte KEY_CONTACT_TYPE  = 4;

    static final byte KEY_MESSAGE_INDEX = 1;
    static final byte KEY_MESSAGE_MESSAGE = 2;
    static final byte KEY_MESSAGE_HASH = 3;

    static final UUID watchAppUuid = UUID.fromString("3eb97e36-d782-4d55-88e5-be70145944cc");

    static final byte MASK_IS_LAST = (byte) 0x80;
    static final byte MASK_IS_HISTORY = 0x40;
    static final byte MASK_IS_EMPTY = 0x20;
}
