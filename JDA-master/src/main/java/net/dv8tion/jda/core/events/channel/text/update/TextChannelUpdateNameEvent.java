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
package net.dv8tion.jda.core.events.channel.text.update;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.TextChannel;

/**
 * Indicates that a {@link net.dv8tion.jda.core.entities.TextChannel TextChannel}'s name changed.
 *
 * <p>Can be used to detect when a TextChannel name changes and get its previous name.
 *
 * <p>Identifier: {@code name}
 */
public class TextChannelUpdateNameEvent extends GenericTextChannelUpdateEvent<String>
{
    public static final String IDENTIFIER = "name";

    public TextChannelUpdateNameEvent(JDA api, long responseNumber, TextChannel channel, String oldName)
    {
        super(api, responseNumber, channel, oldName, channel.getName(), IDENTIFIER);
    }

    /**
     * The old name
     *
     * @return The old name
     */
    public String getOldName()
    {
        return getOldValue();
    }

    /**
     * The new name
     *
     * @return The new name
     */
    public String getNewName()
    {
        return getNewValue();
    }
}
