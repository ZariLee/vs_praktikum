package com.vs.starnet.star.repository;

import com.vs.starnet.star.model.Component;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * manages component objects and enables setting, getting, deleting and searching them
 */
@Repository
public class ComponentRepository {

    private Map<String, Component> components = new ConcurrentHashMap<>();

    public Component findById(String comUuid) {
        return components.get(comUuid);
    }

    public void save(String comUuid, Component component) {
        components.put(comUuid, component);
    }

    public void delete(String comUuid) {
        components.remove(comUuid);
    }

    public boolean existsById(String comUuid) {
        return components.containsKey(comUuid);
    }

    public Map<String, Component> findAll() {
        return components;
    }
}
