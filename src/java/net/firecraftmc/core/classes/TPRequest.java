package net.firecraftmc.core.classes;

import java.util.UUID;

public class TPRequest {
    
    private final UUID requester;
    private final UUID requested;
    private final long expire;
    
    public TPRequest(UUID requester, UUID requested, long expire) {
        this.requester = requester;
        this.requested = requested;
        this.expire = expire;
    }
    
    public UUID getRequester() {
        return requester;
    }
    
    public UUID getRequested() {
        return requested;
    }
    
    public long getExpire() {
        return expire;
    }
}