package atatar.com.pebbledialer;

import java.util.UUID;

/**
 * Created by X550L-User1 on 12-Nov-14.
 */
class Constants {
    static final byte KEY_TYPE = 0;
    static final byte TYPE_REQUEST_CONTACTS = 1;
    static final byte TYPE_REQUEST_DIAL = 2;
    static final byte TYPE_REQUEST_HANG_CALL = 3;
    static final byte TYPE_REQUEST_ANSWER_CALL = 4;
    static final byte TYPE_REQUEST_SILENCE = 5;
    static final byte TYPE_CONTACT = 1;

    static final byte KEY_PHONE_NUMBER = 1;

    static final byte KEY_CONTACT_INDEX = 1;
    static final byte KEY_CONTACT_NAME = 2;
    static final byte KEY_CONTACT_PHONE = 3;
    static final byte KEY_CONTACT_TYPE  = 4;

    static final UUID watchAppUuid = UUID.fromString("3eb97e36-d782-4d55-88e5-be70145944cc");
}
