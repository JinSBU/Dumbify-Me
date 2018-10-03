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

package net.dv8tion.jda.core.entities.impl;

import net.dv8tion.jda.client.entities.Call;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.requests.*;
import net.dv8tion.jda.core.requests.restaction.MessageAction;
import net.dv8tion.jda.core.utils.cache.UpstreamReference;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class PrivateChannelImpl implements PrivateChannel
{
    private final long id;
    private final UpstreamReference<User> user;

    private long lastMessageId;
    private Call currentCall = null;
    private boolean fake = false;

    public PrivateChannelImpl(long id, User user)
    {
        this.id = id;
        this.user = new UpstreamReference<>(user);
    }

    @Override
    public User getUser()
    {
        return user.get();
    }

    @Override
    public long getLatestMessageIdLong()
    {
        final long messageId = lastMessageId;
        if (messageId < 0)
            throw new IllegalStateException("No last message id found.");
        return messageId;
    }

    @Override
    public boolean hasLatestMessage()
    {
        return lastMessageId > 0;
    }

    @Override
    public String getName()
    {
        return getUser().getName();
    }

    @Override
    public ChannelType getType()
    {
        return ChannelType.PRIVATE;
    }

    @Override
    public JDA getJDA()
    {
        return getUser().getJDA();
    }

    @Override
    public RestAction<Void> close()
    {
        Route.CompiledRoute route = Route.Channels.DELETE_CHANNEL.compile(getId());
        return new RestAction<Void>(getJDA(), route)
        {
            @Override
            protected void handleResponse(Response response, Request<Void> request)
            {
                if (response.isOk())
                    request.onSuccess(null);
                else
                    request.onFailure(response);
            }
        };
    }

    @Override
    public List<RequestFuture<Void>> purgeMessages(List<? extends Message> messages)
    {
        if (messages == null || messages.isEmpty())
            return Collections.emptyList();
        for (Message m : messages)
        {
            if (m.getAuthor().equals(getJDA().getSelfUser()))
                continue;
            throw new IllegalArgumentException("Cannot delete messages of other users in a private channel");
        }
        return PrivateChannel.super.purgeMessages(messages);
    }

    @Override
    public long getIdLong()
    {
        return id;
    }

    @Override
    public boolean isFake()
    {
        return fake;
    }

    @Override
    public RestAction<Call> startCall()
    {
        return null;
    }

    @Override
    public Call getCurrentCall()
    {
        return currentCall;
    }

    @Override
    public MessageAction sendMessage(CharSequence text)
    {
        checkBot();
        return PrivateChannel.super.sendMessage(text);
    }

    @Override
    public MessageAction sendMessage(MessageEmbed embed)
    {
        checkBot();
        return PrivateChannel.super.sendMessage(embed);
    }

    @Override
    public MessageAction sendMessage(Message msg)
    {
        checkBot();
        return PrivateChannel.super.sendMessage(msg);
    }

    @Override
    public MessageAction sendFile(InputStream data, String fileName, Message message)
    {
        checkBot();
        return PrivateChannel.super.sendFile(data, fileName, message);
    }

    public PrivateChannelImpl setFake(boolean fake)
    {
        this.fake = fake;
        return this;
    }

    public PrivateChannelImpl setCurrentCall(Call currentCall)
    {
        this.currentCall = currentCall;
        return this;
    }

    public PrivateChannelImpl setLastMessageId(long id)
    {
        this.lastMessageId = id;
        return this;
    }

    // -- Object --


    @Override
    public int hashCode()
    {
        return Long.hashCode(id);
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof PrivateChannelImpl
                && this.id == ((PrivateChannelImpl) obj).id;
    }

    @Override
    public String toString()
    {
        return "PC:" + getUser().getName() + '(' + getId() + ')';
    }

    private void checkBot()
    {
        if (getUser().isBot() && getJDA().getAccountType() == AccountType.BOT)
            throw new UnsupportedOperationException("Cannot send a private message between bots.");
    }
}
