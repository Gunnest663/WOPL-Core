/*
 * This file is part of the Soapbox Race World core source code.
 * If you use any of this code for third-party purposes, please provide attribution.
 * Copyright (c) 2020.
 */

package com.soapboxrace.core.bo;

import io.lettuce.core.KeyValue;
import io.lettuce.core.ScanIterator;
import io.lettuce.core.api.StatefulRedisConnection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;

/**
 * Responsible for managing the multiplayer matchmaking system.
 * This deals with 2 classes of events: restricted and open.
 * When asked for a persona for a given car class, the matchmaker
 * will check if that class is open or restricted. Open events will receive
 * players of any class, while restricted events will only receive players of
 * the required class.
 *
 * @author heyitsleo
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class MatchmakingBO {

    @EJB
    private RedisBO redisBO;

    @EJB
    private ParameterBO parameterBO;

    private boolean enabled;
    private StatefulRedisConnection<String, String> redisConnection;

    @PostConstruct
    public void initialize() {
        this.enabled = parameterBO.getBoolParam("ENABLE_REDIS");
        if (this.enabled) {
            this.redisConnection = this.redisBO.getConnection();
        }
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("MatchmakingBO shutdown");

        if (this.enabled) {
            this.redisConnection.sync().del("matchmaking_queue");
        }
    }

    /**
     * Adds the given persona ID to the queue under the given car class.
     *
     * @param personaId The ID of the persona to add to the queue.
     * @param carClass  The class of the persona's current car.
     */
    public void addPlayerToQueue(Long personaId, Integer carClass) {
        if (this.enabled) {
            this.redisConnection.sync().hset("matchmaking_queue", personaId.toString(), carClass.toString());
        }
    }

    /**
     * Removes the given persona ID from the queue.
     *
     * @param personaId The ID of the persona to remove from the queue.
     */
    public void removePlayerFromQueue(Long personaId) {
        if (this.enabled) {
            this.redisConnection.sync().hdel("matchmaking_queue", personaId.toString());
        }
    }

    /**
     * Gets the ID of a persona from the queue, as long as that persona is listed under the given car class.
     *
     * @param carClass The car class hash to find a persona in.
     * @return The ID of the persona, or {@literal -1} if no persona was found.
     */
    public Long getPlayerFromQueue(Integer carClass) {
        if (!this.enabled)
            return -1L;

        ScanIterator<KeyValue<String, String>> iterator = ScanIterator.hscan(this.redisConnection.sync(), "matchmaking_queue");
        long personaId = -1L;

        while (iterator.hasNext()) {
            KeyValue<String, String> keyValue = iterator.next();

            if (carClass == 607077938 || Integer.parseInt(keyValue.getValue()) == carClass) {
                personaId = Long.parseLong(keyValue.getKey());
                break;
            }
        }

        return personaId;
    }

    /**
     * Add the given event ID to the list of ignored events for the given persona ID.
     *
     * @param personaId the persona ID
     * @param eventId   the event ID
     */
    public void ignoreEvent(long personaId, long eventId) {
        if (this.enabled) {
            this.redisConnection.sync().sadd("ignored_events." + personaId, Long.toString(eventId));
        }
    }

    /**
     * Resets the list of ignored events for the given persona ID
     *
     * @param personaId the persona ID
     */
    public void resetIgnoredEvents(long personaId) {
        if (this.enabled) {
            this.redisConnection.sync().del("ignored_events." + personaId);
        }
    }

    /**
     * Checks if the given event ID is in the list of ignored events for the given persona ID
     *
     * @param personaId the persona ID
     * @param eventId   the event ID
     * @return {@code true} if the given event ID is in the list of ignored events for the given persona ID
     */
    public boolean isEventIgnored(long personaId, long eventId) {
        if (this.enabled) {
            return this.redisConnection.sync().sismember("ignored_events." + personaId, Long.toString(eventId));
        }

        return false;
    }
}
