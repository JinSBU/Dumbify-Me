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
package net.dv8tion.jda.core.events.channel.text;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;

/**
 * Indicates that a {@link net.dv8tion.jda.core.entities.TextChannel TextChannel} event was fired.
 * <br>Every TextChannelEvent is an instance of this event and can be casted.
 *
 * <p>Can be used to detect any TextChannelEvent.
 */
public abstract class GenericTextChannelEvent extends Event
{
    private final TextChannel channel;

    public GenericTextChannelEvent(JDA api, long responseNumber, TextChannel channel)
    {
        super(api, responseNumber);
        this.channel = channel;
    }

    /**
     * The {@link net.dv8tion.jda.core.entities.TextChannel TextChannel}
     *
     * @return The TextChannel
     */
    public TextChannel getChannel()
    {
        return channel;
    }

    /**
     * The {@link net.dv8tion.jda.core.entities.Guild Guild}
     * <br>Shortcut for {@code getChannel().getGuild()}
     *
     * @return The Guild
     */
    public Guild getGuild()
    {
        return channel.getGuild();
    }
}
