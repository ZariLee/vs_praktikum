package com.vs.starnet.star.repository;

import java.util.Map;

/**
 * Repository interface to set guidelines for repository crud methods
 * @param <K> repository object id
 * @param <V> repository object
 */
public interface InMemoryCrudRepository<K, V> {
    V findById(K id);
    void save(K id, V value);
    void delete(K id);
    boolean existsById(K id);
    Map<K,V> findAll();
    long count();
}

