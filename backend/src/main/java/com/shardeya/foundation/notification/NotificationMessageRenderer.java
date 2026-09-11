package com.shardeya.foundation.notification;

import java.util.Map;

/**
 * WhatsApp/SMS/email bodies are real text sent to a real phone/inbox, not
 * an i18n key a frontend resolves (CLAUDE.md rule #14 is about *error*
 * messages specifically; the in-app bell already renders titleKey/bodyKey
 * client-side in the viewer's current language -- but there is no frontend
 * involved at all for a WhatsApp/SMS/email send, so this is the one place
 * in the app that legitimately renders real sentences server-side, in
 * exactly two languages, per {@link com.shardeya.foundation.auth.AppUser#getLanguage()}).
 * A small, explicit Java switch rather than a resource-bundle/MessageSource
 * setup: there are a handful of types with a real non-in-app channel today
 * (see V7_017), and every params map in this codebase is a named
 * {@code Map<String,Object>}, not the positional args Spring's own
 * MessageFormat-based MessageSource expects -- a bespoke bundle format
 * would need its own placeholder syntax anyway, so a plain method per type
 * is simplest until this list is much longer.
 */
public final class NotificationMessageRenderer {

    private NotificationMessageRenderer() {
    }

    public record RenderedMessage(String subject, String body) {
    }

    public static RenderedMessage render(String typeCode, Map<String, Object> params, String language) {
        boolean hi = "hi".equals(language);
        return switch (typeCode) {
            case "INSTALMENT_DUE_TODAY" -> hi
                    ? new RenderedMessage(null, "याद दिलाना: " + str(params, "buyerName") + " की किस्त ₹" + str(params, "amount")
                        + " प्लॉट " + str(params, "plotNumber") + " के लिए आज देय है।")
                    : new RenderedMessage(null, "Reminder: " + str(params, "buyerName") + "'s instalment of Rs " + str(params, "amount")
                        + " for Plot " + str(params, "plotNumber") + " is due today.");
            case "INSTALMENT_OVERDUE" -> hi
                    ? new RenderedMessage(null, str(params, "buyerName") + " की किस्त ₹" + str(params, "amount")
                        + " प्लॉट " + str(params, "plotNumber") + " के लिए " + str(params, "daysOverdue") + " दिन से बकाया है।")
                    : new RenderedMessage(null, str(params, "buyerName") + "'s instalment of Rs " + str(params, "amount")
                        + " for Plot " + str(params, "plotNumber") + " is " + str(params, "daysOverdue") + " day(s) overdue.");
            case "FOLLOWUP_DUE" -> hi
                    ? new RenderedMessage(null, "आज फॉलो-अप करें: " + str(params, "name"))
                    : new RenderedMessage(null, "Reminder: follow up with " + str(params, "name") + " today.");
            case "COMMISSION_DUE" -> hi
                    ? new RenderedMessage("कमीशन देय है", "ब्रोकर " + str(params, "brokerName") + " को प्लॉट "
                        + str(params, "plotNumber") + " की बिक्री पर ₹" + str(params, "amount") + " कमीशन देय है।")
                    : new RenderedMessage("Broker commission due", "A commission of Rs " + str(params, "amount")
                        + " is now due to broker " + str(params, "brokerName") + " for Plot " + str(params, "plotNumber") + ".");
            case "STAFF_ADDED" -> hi
                    ? new RenderedMessage("Shardeya में जोड़ा गया", str(params, "name") + " को भूमिका " + str(params, "role") + " के साथ जोड़ा गया है।")
                    : new RenderedMessage("Added to Shardeya", str(params, "name") + " was added with the role " + str(params, "role") + ".");
            case "STAFF_DEACTIVATED" -> hi
                    ? new RenderedMessage("टीम सदस्य निष्क्रिय किया गया", str(params, "name") + " को निष्क्रिय कर दिया गया है।")
                    : new RenderedMessage("Team member deactivated", str(params, "name") + " has been deactivated.");
            default -> hi
                    ? new RenderedMessage("Shardeya से सूचना", "आपके पास एक नई सूचना है। ऐप में विवरण देखें।")
                    : new RenderedMessage("Shardeya notification", "You have a new notification. See the app for details.");
        };
    }

    private static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
