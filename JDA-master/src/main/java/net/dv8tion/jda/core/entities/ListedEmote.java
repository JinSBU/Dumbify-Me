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

package net.dv8tion.jda.core.entities;

/**
 * Represents an emote retrieved from {@link Guild#retrieveEmotes()} or {@link Guild#retrieveEmoteById(long)}
 *
 * @see   net.dv8tion.jda.core.entities.Emote Emote
 *
 * @since 3.8.0
 */
public interface ListedEmote extends Emote
{
    /**
     * The user who created this Emote
     *
     * <p>This is only available for manually retrieved emotes from {@link Guild#retrieveEmotes()}
     * and {@link Guild#retrieveEmoteById(long)}.
     * <br>Requires {@link net.dv8tion.jda.core.Permission#MANAGE_EMOTES Permission.MANAGE_EMOTES}.
     *
     * @throws IllegalStateException
     *         If this emote does not have user information
     *
     * @return The user who created this Emote
     *
     * @see    #hasUser()
     */
    User getUser();

    /**
     * Whether this Emote has information about the creator.
     * <br>If this is false, {@link #getUser()} throws an {@link IllegalStateException}.
     *
     * <p>This is only available for manually retrieved emotes from {@link Guild#retrieveEmotes()}
     * and {@link Guild#retrieveEmoteById(long)}.
     * <br>Requires {@link net.dv8tion.jda.core.Permission#MANAGE_EMOTES Permission.MANAGE_EMOTES}.
     *
     * @return True, if this emote has an owner
     */
    boolean hasUser();
}
