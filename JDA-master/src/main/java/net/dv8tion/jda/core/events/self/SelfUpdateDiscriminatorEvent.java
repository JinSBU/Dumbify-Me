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

package net.dv8tion.jda.core.events.self;

import net.dv8tion.jda.core.JDA;

/**
 * Indicates that the discriminator of the current user changed.
 *
 * <p>Can be used to retrieve the old discriminator.
 *
 * <p>Identifier: {@code discriminator}
 */
public class SelfUpdateDiscriminatorEvent extends GenericSelfUpdateEvent<String>
{
    public static final String IDENTIFIER = "discriminator";

    public SelfUpdateDiscriminatorEvent(JDA api, long responseNumber, String oldDiscriminator)
    {
        super(api, responseNumber, oldDiscriminator, api.getSelfUser().getDiscriminator(), IDENTIFIER);
    }

    /**
     * The old discriminator
     *
     * @return The old discriminator
     */
    public String getOldDiscriminator()
    {
        return getOldValue();
    }

    /**
     * The new discriminator
     *
     * @return The new discriminator
     */
    public String getNewDiscriminator()
    {
        return getNewValue();
    }
}
