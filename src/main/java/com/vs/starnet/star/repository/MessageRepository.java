package com.vs.starnet.star.repository;

import com.vs.starnet.star.model.Message;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author itakurah (Niklas Hoefflin)
 * GitHub: <a href="https://github.com/itakurah">itakurah</a>
 * LinkedIn: <a href="https://www.linkedin.com/in/niklashoefflin">Niklas Hoefflin</a>
 */
@Repository
public class MessageRepository {

    private Map<String, Message> messages = new ConcurrentHashMap<>();

    public Message findById(String msgId) {
        return messages.get(msgId);
    }

    public void save(String msgId, Message message) {
        messages.put(msgId, message);
    }

    public void delete(String msgId) {
        messages.remove(msgId);
    }

    public boolean existsById(String msgId) {
        return messages.containsKey(msgId);
    }

    public Map<String, Message> findAll() {
        return messages;
    }
}
