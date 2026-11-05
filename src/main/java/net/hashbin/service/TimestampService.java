package net.hashbin.service;

import net.hashbin.model.Timestamp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Persistence boundary for timestamps. Kept separate from the REST resource so
 *  the {@code @Transactional} write can be rolled back independently of the
 *  HTTP response mapping. */
@ApplicationScoped
public class TimestampService {

    @Transactional
    public void save(Timestamp timestamp) {
        timestamp.persist();
    }
}
