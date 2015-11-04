package ru.mirea.oop.practice.coursej.s131245;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mirea.oop.practice.coursej.api.vk.FriendsApi;
import ru.mirea.oop.practice.coursej.api.vk.MessagesApi;
import ru.mirea.oop.practice.coursej.api.vk.entities.Contact;
import ru.mirea.oop.practice.coursej.impl.vk.ext.ServiceBotsExtension;

import java.io.IOException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Created by aleksejpluhin on 13.10.15.
 */
public final class VkStatistic extends ServiceBotsExtension {
    private static final Logger logger = LoggerFactory.getLogger(VkStatistic.class);
    //FIXME: Зачем static поле?
    private static final Map<Long, ArrayList<Session>> mapSession = new HashMap<>();
    private static final Map<Long, Contact> friendsMap = new HashMap<>();
    private final MessagesApi msgApi;
    private static final ThreadLocal<DateFormat> threadFormat = new ThreadLocal<>();
    private static boolean alreadySend = false;
    private static final String FRIENDS_FIELDS = "nickname, " +
            "domain, " +
            "sex, " +
            "bdate, " +
            "city, " +
            "country, " +
            "timezone, " +
            "photo_50, " +
            "photo_100, " +
            "photo_200_orig, " +
            "has_mobile, " +
            "contacts, " +
            "education, " +
            "online, " +
            "relation, " +
            "last_seen, " +
            "status, " +
            "can_write_private_message, " +
            "can_see_all_posts, " +
            "can_post, " +
            "universities";

    static {
        mapSession.clear();
    }

    public VkStatistic() throws Exception {
        super("vk.services.VkStatistic");
        this.msgApi = api.getMessages();
    }

    private static DateFormat getFormat() {
        DateFormat format = threadFormat.get();
        if (format == null) {
            format = new SimpleDateFormat("HH:mm");
            threadFormat.set(format);
        }
        return format;
    }


    @Override
    protected void doEvent(Event event) {

        if (mapSession.isEmpty()) {
            firstPutOfFriends();
        }

        switch (event.type) {

            case FRIEND_ONLINE: {
                eventOnline(event);
                break;
            }

            case FRIEND_OFFLINE: {
                eventOffline(event);
                break;
            }


            case MESSAGE_RECEIVE: {
                eventMessageReceive(event);
                break;
            }

            case TIMEOUT: {
                eventTimeout();
                break;
            }


            default:
                logger.debug("" + (event.object == null ? event.type : event.type + "|" + event.object));
                break;
        }


    }


    @Override
    public String description() {
        return "Сервис статистики пользователей \"Вконтакте\"";
    }

    public void firstPutOfFriends() {

        putFriendsMap();

        for (Map.Entry<Long, Contact> friend : friendsMap.entrySet()) {
            ArrayList<Session> arrayList = new ArrayList<>();
            if (friend.getValue().online == 1) {
                Session session = new Session(new Date());
                arrayList.add(session);
                mapSession.put(friend.getValue().id, arrayList);
                logger.debug(friend.getValue().lastName + " - онлайн");
            } else {
                mapSession.put(friend.getValue().id, arrayList);
                logger.debug(friend.getValue().lastName + " - оффлайн");
            }
        }

    }

    public void putFriendsMap() {
        try {
            friendsMap.clear();
            FriendsApi friendsApi = api.getFriends();
            Contact[] contacts = friendsApi.list(null, null, null, null, FRIENDS_FIELDS);
            for (Contact contact : contacts) {
                friendsMap.put(contact.id, contact);
            }
        } catch (Exception e) {
            logger.error("Ошибка получения списка друзей");
        }
    }

    /**FIXME: Разобраться в логике работы */
    public String parse(String msg) {
        try {
            String date = "";
            String text = "";
            String withoutDate = "";
            try {
                String[] split = msg.split(" ");
                date = split[split.length - 1];
                withoutDate = msg.substring(0, msg.lastIndexOf(" "));
                msg = msg.substring(0, msg.lastIndexOf(" "));
            } catch (Exception e) {
                logger.error("даты нет");
            }
            if (msg.split(": ")[1].equals("всех")) {
                text = "Статисткика всех пользователй " + (date.isEmpty() ? "" : " за " + date);
            } else {
                String[] arr;
                if(withoutDate.isEmpty()) {
                    arr = withoutDate.split(": ")[1].split(", ");
                }  else {
                    arr = msg.split(": ")[1].split(", ");
                }

                String people = "";
                for (String humanName : arr) {
                    people += humanName + ", ";
                    text = "Статистика пользователей: " + people.substring(0, people.length() - 2) + (date.isEmpty() ? "" : " за " + date);
                }
            }
            return text;
        } catch (Exception e) {
            logger.error("Ошибка ввода");
        }
        return null;
    }




    public static void eventOnline(Event event) {
        UserOnline userOnline = (UserOnline) event.object;
        Long key = userOnline.getContact().id;

        Session session = new Session(new Date());
        if (mapSession.get(key).isEmpty()) {
            mapSession.get(key).add(session);
        } else {
            try {
                int index = mapSession.get(key).size() - 1;
                if (mapSession.get(key).get(index).getEnd() == null) {
                    mapSession.get(key).remove(index);
                    mapSession.get(key).add(session);
                } else {
                    throw new Exception();
                }
            } catch (Exception e) {
                mapSession.get(key).add(session);
            }

        }
        logger.debug(event.object + " вошел в " + getFormat().format(mapSession.get(key).get(mapSession.get(key).size() - 1).getBegin()));

    }

    public void eventOffline(Event event) {
        UserOffline userOffline = (UserOffline) event.object;
        Long key = userOffline.getContact().id;
        int index = mapSession.get(key).size() - 1;
        String name = ((UserOffline) event.object).getContact().firstName + " " + ((UserOffline) event.object).getContact().lastName;
        if (mapSession.get(key).get(index).getBegin() == null) {
            return;
        }
        mapSession.get(key).get(index).setEnd(new Date());
        logger.debug(name + " вышел в " + getFormat().format(mapSession.get(key).get(mapSession.get(key).size() - 1).getEnd()));
    }

    public void eventMessageReceive(Event event) {
        if (alreadySend) {
            alreadySend = false;
            return;
        }
        Message msg = (Message) event.object;
        Contact contact = msg.contact;
        String text;
        Attachment attachment;
        String attachmentName = null;
        if (contact.id == owner.id) {
            if (msg.text.contains("bot get")) {
                text = parse(msg.text);
                try {
                    attachment = new Attachment(mapSession, friendsMap, api, msg.text);
                    attachmentName = attachment.getAttachmentName();
                } catch (Exception e) {
                    logger.error("Ошибка получения документа");
                    e.printStackTrace();
                }
            } else if (msg.text.equals("help")) {
                text = "Запрос состоит имеет ввид\n1)bot get: Иван Иванов\n2)bog get: всех\n3)bot get: Иванов Иван, Иванова Ивана 10/04/2015\n";
            }
            else {
                return;
            }
            try {

                Integer idMessage = msgApi.send(
                        contact.id,
                        null,
                        null,
                        null,
                        text,
                        null,
                        null,
                        null,
                        attachmentName,
                        null,
                        null

                );
                logger.debug("Сообщение отправлено " + idMessage);

                alreadySend = true;

            } catch (IOException ex) {
                logger.error("Ошибка отправки сообщения", ex);
            } catch (Exception e) {
                logger.debug("Ошибка attachment");
                e.printStackTrace();
            }
        }
    }

    public void eventTimeout() {
        putFriendsMap();
        for (Map.Entry<Long, Contact> current : friendsMap.entrySet()) {
            Long key = current.getValue().id;

            if (!mapSession.get(key).isEmpty()) {
                int index = mapSession.get(key).size() - 1;
                Session lastSession = mapSession.get(key).get(index);
                if ((lastSession.getEnd() == null) && current.getValue().online == 0) {
                    lastSession.setEnd(new Date());
                } else if ((lastSession.getBegin() == null) && current.getValue().online == 1) {
                    Session session = new Session(new Date());
                    mapSession.get(key).add(session);
                }
            }

        }

    }


}





