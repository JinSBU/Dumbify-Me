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

package net.dv8tion.jda.core.events.guild.voice;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Member;

/**
 * Indicates that a {@link net.dv8tion.jda.core.entities.Member Member} was (un-)deafened by a moderator.
 *
 * <p>Can be used to detect when a member is deafened or un-deafened by a moderator.
 */
public class GuildVoiceGuildDeafenEvent extends GenericGuildVoiceEvent
{
    protected final boolean guildDeafened;

    public GuildVoiceGuildDeafenEvent(JDA api, long responseNumber, Member member)
    {
        super(api, responseNumber, member);
        this.guildDeafened = member.getVoiceState().isGuildDeafened();
    }

    /**
     * Whether the member was deafened by a moderator in this event
     *
     * @return True, if a moderator deafened this member,
     *         <br>False, if a moderator un-deafened this member
     */
    public boolean isGuildDeafened()
    {
        return guildDeafened;
    }
}
