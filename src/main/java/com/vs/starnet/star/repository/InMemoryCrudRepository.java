package com.vs.starnet.star.repository;

import java.util.Map;

public interface InMemoryCrudRepository<K, V> {
    V findById(K id);
    void save(K id, V value);
    void delete(K id);
    boolean existsById(K id);
    Map<K,V> findAll();
    long count();
}

