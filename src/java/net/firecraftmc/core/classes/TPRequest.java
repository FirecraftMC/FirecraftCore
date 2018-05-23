package net.firecraftmc.core.classes;

import java.util.UUID;

/**
 * Class that represents a teleport request
 */
public class TPRequest {
    
    private final UUID requester;
    private final UUID requested;
    private final long expire;

    /**
     * @param requester The uuid of the one who requested the teleport
     * @param requested The uuid of the one who is the target of the request
     * @param expire The expire time in milliseconds when the request expires
     */
    public TPRequest(UUID requester, UUID requested, long expire) {
        this.requester = requester;
        this.requested = requested;
        this.expire = expire;
    }

    /**
     * @return The requester of the teleport request
     */
    public UUID getRequester() {
        return requester;
    }

    /**
     * @return The target of the Teleport Request
     */
    public UUID getRequested() {
        return requested;
    }

    /**
     * @return When the request expires
     */
    public long getExpire() {
        return expire;
    }
}