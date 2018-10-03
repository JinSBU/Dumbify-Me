/*
 * Copyright 2015-2018 Austin Keener & Michael Ritter & Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.core.handle;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.WebSocketCode;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.requests.WebSocketClient;
import net.dv8tion.jda.core.utils.JDALogger;
import net.dv8tion.jda.core.utils.MiscUtil;
import net.dv8tion.jda.core.utils.cache.UpstreamReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class GuildSetupController
{
    protected static final Logger log = JDALogger.getLog(GuildSetupController.class);
    private final UpstreamReference<JDAImpl> api;
    private final TLongObjectMap<GuildSetupNode> setupNodes = new TLongObjectHashMap<>();
    private final TLongSet chunkingGuilds = new TLongHashSet();
    private final TLongSet syncingGuilds;
    private int incompleteCount = 0;
    private int syncingCount = 0;

    StatusListener listener = (id, oldStatus, newStatus) -> log.trace("[{}] Updated status {}->{}", id, oldStatus, newStatus);

    public GuildSetupController(JDAImpl api)
    {
        this.api = new UpstreamReference<>(api);
        if (isClient())
            syncingGuilds = new TLongHashSet();
        else
            syncingGuilds = null;
    }

    JDAImpl getJDA()
    {
        JDAImpl tmp = api.get();
        if (tmp == null)
            throw new IllegalStateException();
        return tmp;
    }

    boolean isClient()
    {
        return getJDA().getAccountType() == AccountType.CLIENT;
    }

    void addGuildForChunking(long id, boolean join)
    {
        log.trace("Adding guild for chunking ID: {}", id);
        if (join || incompleteCount <= 0)
        {
            if (incompleteCount <= 0)
            {
                // this happens during runtime -> chunk right away
                sendChunkRequest(id);
                return;
            }
            incompleteCount++;
        }
        chunkingGuilds.add(id);
        tryChunking();
    }

    void addGuildForSyncing(long id, boolean join)
    {
        if (!isClient())
            return;
        log.trace("Adding guild for syncing ID: {}", id);
        if (join || incompleteCount <= 0)
        {
            if (incompleteCount <= 0)
            {
                // this happens during runtime -> sync right away
                sendSyncRequest(new JSONArray().put(id));
                return;
            }
            syncingCount++;
        }
        syncingGuilds.add(id);
        trySyncing();
    }

    void remove(long id)
    {
        setupNodes.remove(id);
    }

    // Called by:

    // - ReadyHandler
    // - GuildSetupNode
    public void ready(long id)
    {
        setupNodes.remove(id);
        WebSocketClient client = getJDA().getClient();
        if (!client.isReady() && --incompleteCount < 1)
            client.ready();
        else
            tryChunking();
    }

    // - ReadyHandler
    public boolean setIncompleteCount(int count)
    {
        log.debug("Setting incomplete count to {}", count);
        this.incompleteCount = count;
        this.syncingCount = count;
        boolean ready = count == 0;
        if (ready)
            getJDA().getClient().ready();
        return !ready;
    }

    // - ReadyHandler
    public void onReady(long id, JSONObject obj)
    {
        log.trace("Adding id to setup cache {}", id);
        GuildSetupNode node = new GuildSetupNode(id, this, false);
        setupNodes.put(id, node);
        node.handleReady(obj);
        if (node.markedUnavailable)
        {
            if (node.sync)
            {
                syncingCount--;
                trySyncing();
            }
            incompleteCount--;
            tryChunking();
        }
    }

//    // - WebSocketClient
//    public void onResume(boolean isInit)
//    {
//        if (setupNodes.isEmpty())
//            return;
//        if (isInit && incompleteCount > 0)
//        {
//            //Override current chunking and syncing state - we were interrupted
//            // this count will be adjusted by addGuildForX(id, join) later, we need to fix the displacement here
//            Set<GuildSetupNode> joinedGuilds = setupNodes.valueCollection().stream().filter((node) -> node.join).collect(Collectors.toSet());
//            long displacementChunking = joinedGuilds.stream().filter((node) -> node.requestedChunk).count();
//            long displacementSyncing  = joinedGuilds.stream().filter((node) -> node.sync).count();
//            this.incompleteCount -= (int) displacementChunking;
//            this.syncingCount -= (int) displacementSyncing;
//        }
//
//        setupNodes.forEachEntry((id, node) -> {
//            if (node.sync)
//                addGuildForSyncing(id, node.join);
//            if (node.requestedChunk)
//                addGuildForChunking(id, node.join);
//            return true;
//        });
//    }

    // - ReadyHandler (for client accounts)
    // - GuildCreateHandler
    public void onCreate(long id, JSONObject obj)
    {
        boolean available = obj.isNull("unavailable") || !obj.getBoolean("unavailable");
        log.trace("Received guild create for id: {} available: {}", id, available);
        GuildSetupNode node = setupNodes.get(id);
        if (node == null)
        {
            // this is a join event
            node = new GuildSetupNode(id, this, true);
            setupNodes.put(id, node);
            // do not increment incomplete counter, it is only relevant to init guilds
        }
        else if (node.markedUnavailable && available && incompleteCount > 0)
        {
            //Looks like this guild decided to become available again during startup
            // that means we can now consider it for ReadyEvent status again!
            if (node.sync)
                syncingCount++;
            incompleteCount++;
        }
        node.handleCreate(obj);
    }

    // - GuildDeleteHandler
    public boolean onDelete(long id, JSONObject obj)
    {
        boolean available = obj.isNull("unavailable") || !obj.getBoolean("unavailable");
        GuildSetupNode node = setupNodes.get(id);
        if (node == null)
            return false;
        log.debug("Received guild delete for id: {} available: {}", id, available);
        if (!available)
        {
            if (!node.markedUnavailable && !node.requestedChunk)
            {
                node.markedUnavailable = true; // this prevents repeated decrements from duplicate events
                if (node.sync)
                {
                    syncingCount--;
                    trySyncing();
                }
                if (incompleteCount > 0)
                {
                    incompleteCount--;
                    tryChunking();
                }
            }
            node.reset();
        }
        else
        {
            node.cleanup(); // clear EventCache
            // this was actually deleted
            if (node.join && !node.requestedChunk)
                remove(id);
            else
                ready(id);
        }
        log.debug("Updated incompleteCount to {} and syncCount to {}", incompleteCount, syncingCount);
        return true;
    }

    // - GuildMemberChunkHandler
    public void onMemberChunk(long id, JSONArray chunk)
    {
        log.debug("Received member chunk for guild id: {} size: {}", id, chunk.length());
        GuildSetupNode node = setupNodes.get(id);
        if (node != null)
            node.handleMemberChunk(chunk);
    }

    // - GuildMemberAddHandler
    public boolean onAddMember(long id, JSONObject member)
    {
        GuildSetupNode node = setupNodes.get(id);
        if (node == null)
            return false;
        log.debug("Received GUILD_MEMBER_ADD during setup, adding member to guild. GuildID: {}", id);
        node.handleAddMember(member);
        return true;
    }

    // - GuildMemberRemoveHandler
    public boolean onRemoveMember(long id, JSONObject member)
    {
        GuildSetupNode node = setupNodes.get(id);
        if (node == null)
            return false;
        log.debug("Received GUILD_MEMBER_REMOVE during setup, removing member from guild. GuildID: {}", id);
        node.handleRemoveMember(member);
        return true;
    }

    public void onSync(long id, JSONObject obj)
    {
        GuildSetupNode node = setupNodes.get(id);
        if (node != null)
            node.handleSync(obj);
    }

    // Anywhere \\

    public boolean isLocked(long id)
    {
        return setupNodes.containsKey(id);
    }

    public void cacheEvent(long guildId, JSONObject event)
    {
        GuildSetupNode node = setupNodes.get(guildId);
        if (node != null)
            node.cacheEvent(event);
        else
            log.warn("Attempted to cache event for a guild that is not locked. {}", event, new IllegalStateException());
    }

    public void clearCache()
    {
        setupNodes.clear();
        chunkingGuilds.clear();
        incompleteCount = 0;
    }

    public boolean containsMember(long userId, @Nullable GuildSetupNode excludedNode)
    {
        for (TLongObjectIterator<GuildSetupNode> it = setupNodes.iterator(); it.hasNext();)
        {
            it.advance();
            GuildSetupNode node = it.value();
            if (node != excludedNode && node.containsMember(userId))
                return true;
        }
        return false;
    }

    public Set<GuildSetupNode> getSetupNodes()
    {
        return new HashSet<>(setupNodes.valueCollection());
    }

    public Set<GuildSetupNode> getSetupNodes(Status status)
    {
        return getSetupNodes().stream().filter((node) -> node.status == status).collect(Collectors.toSet());
    }

    public GuildSetupNode getSetupNodeById(long id)
    {
        return setupNodes.get(id);
    }

    public GuildSetupNode getSetupNodeById(String id)
    {
        return getSetupNodeById(MiscUtil.parseSnowflake(id));
    }

    public void setStatusListener(StatusListener listener)
    {
        this.listener = Objects.requireNonNull(listener);
    }

    // Chunking

    private void sendChunkRequest(Object obj)
    {
        log.debug("Sending chunking requests for {} guilds", obj instanceof JSONArray ? ((JSONArray) obj).length() : 1);

        getJDA().getClient().chunkOrSyncRequest(
            new JSONObject()
                .put("op", WebSocketCode.MEMBER_CHUNK_REQUEST)
                .put("d", new JSONObject()
                    .put("guild_id", obj)
                    .put("query", "")
                    .put("limit", 0)));
    }

    private void tryChunking()
    {
        if (chunkingGuilds.size() >= 50)
        {
            // request chunks
            final JSONArray subset = new JSONArray();
            for (final TLongIterator it = chunkingGuilds.iterator(); subset.length() < 50; )
            {
                subset.put(it.next());
                it.remove();
            }
            sendChunkRequest(subset);
        }
        if (incompleteCount > 0 && chunkingGuilds.size() == incompleteCount)
        {
            // request last chunks
            final JSONArray array = new JSONArray();
            chunkingGuilds.forEach((guild) -> {
                array.put(guild);
                return true;
            });
            chunkingGuilds.clear();
            sendChunkRequest(array);
        }
    }

    // Syncing

    private void sendSyncRequest(JSONArray arr)
    {
        log.debug("Sending syncing requests for {} guilds", arr.length());

        getJDA().getClient().chunkOrSyncRequest(
            new JSONObject()
                .put("op", WebSocketCode.GUILD_SYNC)
                .put("d", arr));
    }

    private void trySyncing()
    {
        if (syncingGuilds.size() >= 50)
        {
            // request chunks
            final JSONArray subset = new JSONArray();
            for (final TLongIterator it = syncingGuilds.iterator(); subset.length() < 50; )
            {
                subset.put(it.next());
                it.remove();
            }
            sendSyncRequest(subset);
            syncingCount -= subset.length();
        }
        if (syncingCount > 0 && syncingGuilds.size() == syncingCount)
        {
            final JSONArray array = new JSONArray();
            syncingGuilds.forEach((guild) -> {
                array.put(guild);
                return true;
            });
            syncingGuilds.clear();
            sendSyncRequest(array);
            syncingCount = 0;
        }
    }

    public enum Status
    {
        INIT,
        SYNCING,
        CHUNKING,
        BUILDING,
        READY,
        UNAVAILABLE,
        REMOVED
    }

    @FunctionalInterface
    public interface StatusListener
    {
        void onStatusChange(long guildId, Status oldStatus, Status newStatus);
    }
}
