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

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.utils.Checks;
import net.dv8tion.jda.core.utils.PermissionUtil;
import net.dv8tion.jda.core.utils.cache.CacheFlag;
import net.dv8tion.jda.core.utils.cache.UpstreamReference;

import javax.annotation.Nullable;
import java.awt.Color;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class MemberImpl implements Member
{
    private static final ZoneOffset OFFSET = ZoneOffset.of("+00:00");
    private final UpstreamReference<GuildImpl> guild;
    private final User user;
    private final Set<Role> roles = new HashSet<>();
    private final GuildVoiceState voiceState;

    private String nickname;
    private long joinDate;
    private Game game;
    private OnlineStatus onlineStatus = OnlineStatus.OFFLINE;

    public MemberImpl(GuildImpl guild, User user)
    {
        this.guild = new UpstreamReference<>(guild);
        this.user = user;
        JDAImpl jda = (JDAImpl) getJDA();
        boolean cacheState = jda.isCacheFlagSet(CacheFlag.VOICE_STATE) || user.equals(jda.getSelfUser());
        this.voiceState = cacheState ? new GuildVoiceStateImpl(guild, this) : null;
    }

    @Override
    public User getUser()
    {
        return user;
    }

    @Override
    public GuildImpl getGuild()
    {
        return guild.get();
    }

    @Override
    public JDA getJDA()
    {
        return user.getJDA();
    }

    @Override
    public OffsetDateTime getJoinDate()
    {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(joinDate), OFFSET);
    }

    @Override
    public GuildVoiceState getVoiceState()
    {
        return voiceState;
    }

    @Override
    public Game getGame()
    {
        return game;
    }

    @Override
    public OnlineStatus getOnlineStatus()
    {
        return onlineStatus;
    }

    @Override
    public String getNickname()
    {
        return nickname;
    }

    @Override
    public String getEffectiveName()
    {
        return nickname != null ? nickname : user.getName();
    }

    @Override
    public List<Role> getRoles()
    {
        List<Role> roleList = new ArrayList<>(roles);
        roleList.sort(Comparator.reverseOrder());

        return Collections.unmodifiableList(roleList);
    }

    @Override
    public Color getColor()
    {
        final int raw = getColorRaw();
        return raw != Role.DEFAULT_COLOR_RAW ? new Color(raw) : null;
    }

    @Override
    public int getColorRaw()
    {
        for (Role r : getRoles())
        {
            final int colorRaw = r.getColorRaw();
            if (colorRaw != Role.DEFAULT_COLOR_RAW)
                return colorRaw;
        }
        return Role.DEFAULT_COLOR_RAW;
    }

    @Override
    public List<Permission> getPermissions()
    {
        return Collections.unmodifiableList(
                Permission.getPermissions(
                        PermissionUtil.getEffectivePermission(this)));
    }

    @Override
    public List<Permission> getPermissions(Channel channel)
    {
        if (!getGuild().equals(channel.getGuild()))
            throw new IllegalArgumentException("Provided channel is not in the same guild as this member!");

        return Collections.unmodifiableList(
                Permission.getPermissions(
                        PermissionUtil.getEffectivePermission(channel, this)));
    }

    @Override
    public boolean hasPermission(Permission... permissions)
    {
        return PermissionUtil.checkPermission(this, permissions);
    }

    @Override
    public boolean hasPermission(Collection<Permission> permissions)
    {
        Checks.notNull(permissions, "Permission Collection");

        return hasPermission(permissions.toArray(Permission.EMPTY_PERMISSIONS));
    }

    @Override
    public boolean hasPermission(Channel channel, Permission... permissions)
    {
        return PermissionUtil.checkPermission(channel, this, permissions);
    }

    @Override
    public boolean hasPermission(Channel channel, Collection<Permission> permissions)
    {
        Checks.notNull(permissions, "Permission Collection");

        return hasPermission(channel, permissions.toArray(Permission.EMPTY_PERMISSIONS));
    }

    @Override
    public boolean canInteract(Member member)
    {
        return PermissionUtil.canInteract(this, member);
    }

    @Override
    public boolean canInteract(Role role)
    {
        return PermissionUtil.canInteract(this, role);
    }

    @Override
    public boolean canInteract(Emote emote)
    {
        return PermissionUtil.canInteract(this, emote);
    }

    @Override
    public boolean isOwner() {
        return this.equals(getGuild().getOwner());
    }

    public MemberImpl setNickname(String nickname)
    {
        this.nickname = nickname;
        return this;
    }

    public MemberImpl setJoinDate(long joinDate)
    {
        this.joinDate = joinDate;
        return this;
    }

    public MemberImpl setGame(Game game)
    {
        this.game = game;
        return this;
    }

    public MemberImpl setOnlineStatus(OnlineStatus onlineStatus)
    {
        this.onlineStatus = onlineStatus;
        return this;
    }

    public Set<Role> getRoleSet()
    {
        return roles;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Member))
            return false;

        Member oMember = (Member) o;
        return this == oMember || (oMember.getUser().equals(user) && oMember.getGuild().equals(getGuild()));
    }

    @Override
    public int hashCode()
    {
        return (getGuild().getId() + user.getId()).hashCode();
    }

    @Override
    public String toString()
    {
        return "MB:" + getEffectiveName() + '(' + user.toString() + " / " + getGuild().toString() +')';
    }

    @Override
    public String getAsMention()
    {
        return nickname == null ? user.getAsMention() : "<@!" + user.getIdLong() + '>';
    }

    @Nullable
    @Override
    public TextChannel getDefaultChannel()
    {
        return getGuild().getTextChannelsMap().valueCollection().stream()
                .sorted(Comparator.reverseOrder())
                .filter(c -> hasPermission(c, Permission.MESSAGE_READ))
                .findFirst().orElse(null);
    }
}
