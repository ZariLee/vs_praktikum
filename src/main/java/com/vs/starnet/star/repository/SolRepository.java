package com.vs.starnet.star.repository;

import com.vs.starnet.star.model.Component;
import com.vs.starnet.star.model.Sol;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 */

@Repository
public class SolRepository implements InMemoryCrudRepository<String, Sol> {
    static final Logger LOGGER = LogManager.getRootLogger();

    // Two maps for active and inactive Sols
    private Map<String, Sol> activeSols = new ConcurrentHashMap<>();
    private Map<String, Sol> inactiveSols = new ConcurrentHashMap<>();

    public Sol findById(String solStarUuid) {
        return activeSols.get(solStarUuid);
    }

    public void save(String solStarUuid, Sol sol) {
        activeSols.put(solStarUuid, sol); // Save the Sol in active map
        LOGGER.log(Level.getLevel("STAR_INFO"), "Star registered: {}", sol);
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "New star count in active map: {}", activeSols.size());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Star list: {}", findAll());
    }

    public void delete(String solStarUuid) {
        Sol removedSol = activeSols.remove(solStarUuid);
        LOGGER.log(Level.getLevel("STAR_INFO"), "Star deleted: {}", solStarUuid);
        if (removedSol != null) {
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Star deregistered from active: {}", solStarUuid);
            // Optionally move to inactive map upon deletion
            inactiveSols.put(solStarUuid, removedSol);
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Star moved to inactive: {}", solStarUuid);
        }
    }

    public boolean existsById(String solStarUuid) {
        return activeSols.containsKey(solStarUuid);
    }

    @Override
    public Map<String, Sol> findAll() {
        return activeSols;
    }

    public Map<String, Sol> findAllInactive() {
        return inactiveSols;
    }

    @Override
    public long count() {
        return activeSols.size();
    }
}

