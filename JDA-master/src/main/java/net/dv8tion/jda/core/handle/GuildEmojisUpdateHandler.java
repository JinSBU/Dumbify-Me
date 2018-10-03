/*
 *     Copyright 2015-2018 Austin Keener & Michael Ritter & Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.core.handle;

import gnu.trove.map.TLongObjectMap;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.impl.EmoteImpl;
import net.dv8tion.jda.core.entities.impl.GuildImpl;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.events.emote.EmoteAddedEvent;
import net.dv8tion.jda.core.events.emote.EmoteRemovedEvent;
import net.dv8tion.jda.core.events.emote.update.EmoteUpdateNameEvent;
import net.dv8tion.jda.core.events.emote.update.EmoteUpdateRolesEvent;
import net.dv8tion.jda.core.utils.cache.CacheFlag;
import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class GuildEmojisUpdateHandler extends SocketHandler
{
    public GuildEmojisUpdateHandler(JDAImpl api)
    {
        super(api);
    }

    @Override
    protected Long handleInternally(JSONObject content)
    {
        if (!getJDA().isCacheFlagSet(CacheFlag.EMOTE))
            return null;
        final long guildId = content.getLong("guild_id");
        if (getJDA().getGuildSetupController().isLocked(guildId))
            return guildId;

        GuildImpl guild = (GuildImpl) getJDA().getGuildMap().get(guildId);
        if (guild == null)
        {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle);
            return null;
        }

        JSONArray array = content.getJSONArray("emojis");
        TLongObjectMap<Emote> emoteMap = guild.getEmoteMap();
        List<Emote> oldEmotes = new ArrayList<>(emoteMap.valueCollection()); //snapshot of emote cache
        List<Emote> newEmotes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject current = array.getJSONObject(i);
            final long emoteId = current.getLong("id");
            EmoteImpl emote = (EmoteImpl) emoteMap.get(emoteId);
            EmoteImpl oldEmote = null;

            if (emote == null)
            {
                emote = new EmoteImpl(emoteId, guild);
                newEmotes.add(emote);
            }
            else
            {
                // emote is in our cache which is why we don't want to remove it in cleanup later
                oldEmotes.remove(emote);
                oldEmote = emote.clone();
            }

            emote.setName(current.getString("name"))
                 .setAnimated(current.optBoolean("animated"))
                 .setManaged(current.getBoolean("managed"));
            //update roles
            JSONArray roles = current.getJSONArray("roles");
            Set<Role> newRoles = emote.getRoleSet();
            Set<Role> oldRoles = new HashSet<>(newRoles); //snapshot of cached roles
            for (int j = 0; j < roles.length(); j++)
            {
                Role role = guild.getRoleById(roles.getString(j));
                if (role != null)
                {
                    newRoles.add(role);
                    oldRoles.remove(role);
                }
            }

            //cleanup old cached roles that were not found in the JSONArray
            for (Role r : oldRoles)
            {
                // newRoles directly writes to the set contained in the emote
                newRoles.remove(r);
            }

            // finally, update the emote
            emoteMap.put(emote.getIdLong(), emote);
            // check for updated fields and fire events
            handleReplace(oldEmote, emote);
        }
        //cleanup old emotes that don't exist anymore
        for (Emote e : oldEmotes)
        {
            emoteMap.remove(e.getIdLong());
            getJDA().getEventManager().handle(
                new EmoteRemovedEvent(
                    getJDA(), responseNumber,
                    e));
        }

        for (Emote e : newEmotes)
        {
            getJDA().getEventManager().handle(
                new EmoteAddedEvent(
                    getJDA(), responseNumber,
                    e));
        }

        return null;
    }

    private void handleReplace(Emote oldEmote, Emote newEmote)
    {
        if (oldEmote == null || newEmote == null) return;

        if (!Objects.equals(oldEmote.getName(), newEmote.getName()))
        {
            getJDA().getEventManager().handle(
                new EmoteUpdateNameEvent(
                    getJDA(), responseNumber,
                    newEmote, oldEmote.getName()));
        }

        if (!CollectionUtils.isEqualCollection(oldEmote.getRoles(), newEmote.getRoles()))
        {
            getJDA().getEventManager().handle(
                new EmoteUpdateRolesEvent(
                    getJDA(), responseNumber,
                    newEmote, oldEmote.getRoles()));
        }

    }

}
