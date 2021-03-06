/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package blazingcache.client;

import java.lang.ref.SoftReference;

/**
 * Una entry nella cache
 *
 * @author enrico.olivelli
 */
public final class CacheEntry {

    private long lastGetTime;
    private final long putTime;
    private final String key;
    private final byte[] serializedData;
    private final long expiretime;
    private SoftReference<Object> reference;

    public CacheEntry(String key, long lastGetTimeNanos, byte[] serializedData, long expiretime, Object deserialized) {
        this.key = key;
        this.lastGetTime = lastGetTimeNanos;
        this.putTime = lastGetTimeNanos;
        this.serializedData = serializedData;
        this.expiretime = expiretime;
        this.reference = deserialized != null ? new SoftReference<>(deserialized) : null;
    }

    public CacheEntry(String key, long lastGetTimeNanos, byte[] serializedData, long expiretime) {
        this.key = key;
        this.lastGetTime = lastGetTimeNanos;
        this.putTime = lastGetTimeNanos;
        this.serializedData = serializedData;
        this.expiretime = expiretime;
    }

    synchronized Object resolveReference(EntrySerializer serializer) throws CacheException {
        Object resolved = reference != null ? reference.get() : null;
        if (resolved == null) {
            resolved = serializer.deserializeObject(key, serializedData);
            reference = new SoftReference<>(resolved);
        }
        return resolved;
    }

    public String getKey() {
        return key;
    }

    public long getPutTime() {
        return putTime;
    }

    public long getLastGetTime() {
        return lastGetTime;
    }

    public void setLastGetTime(final long lastGetTimeNanos) {
        this.lastGetTime = lastGetTimeNanos;
    }

    public byte[] getSerializedData() {
        return serializedData;
    }

    public long getExpiretime() {
        return expiretime;
    }

    @Override
    public String toString() {
        return "CacheEntry{" + "key=" + key + ", lastGetTime=" + lastGetTime + ", serializedData=" + serializedData + ", expiretime=" + expiretime + '}';
    }

}
