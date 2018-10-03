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

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.entities.impl.SelfUserImpl;
import net.dv8tion.jda.core.events.self.*;
import org.json.JSONObject;

import java.util.Objects;

public class UserUpdateHandler extends SocketHandler
{
    public UserUpdateHandler(JDAImpl api)
    {
        super(api);
    }

    @Override
    protected Long handleInternally(JSONObject content)
    {
        SelfUserImpl self = (SelfUserImpl) getJDA().getSelfUser();

        String name = content.getString("username");
        String discriminator = content.getString("discriminator");
        String avatarId = content.optString("avatar", null);
        Boolean verified = content.has("verified") ? content.getBoolean("verified") : null;
        Boolean mfaEnabled = content.has("mfa_enabled") ? content.getBoolean("mfa_enabled") : null;

        //Client only
        String email = content.optString("email", null);
        Boolean mobile = content.has("mobile") ? content.getBoolean("mobile") : null; // mobile device
        Boolean nitro = content.has("premium") ? content.getBoolean("premium") : null; // nitro
        String phoneNumber = content.optString("phone", null); // verified phone number (verification level !)

        if (!Objects.equals(name, self.getName()) || !Objects.equals(discriminator, self.getDiscriminator()))
        {
            String oldName = self.getName();
            self.setName(name);
            getJDA().getEventManager().handle(
                new SelfUpdateNameEvent(
                    getJDA(), responseNumber,
                    oldName));
        }

        if (!Objects.equals(avatarId, self.getAvatarId()))
        {
            String oldAvatarId = self.getAvatarId();
            self.setAvatarId(avatarId);
            getJDA().getEventManager().handle(
                new SelfUpdateAvatarEvent(
                    getJDA(), responseNumber,
                    oldAvatarId));
        }

        if (verified != null && verified != self.isVerified())
        {
            boolean wasVerified = self.isVerified();
            self.setVerified(verified);
            getJDA().getEventManager().handle(
                new SelfUpdateVerifiedEvent(
                    getJDA(), responseNumber,
                    wasVerified));
        }

        if (mfaEnabled != null && mfaEnabled != self.isMfaEnabled())
        {
            boolean wasMfaEnabled = self.isMfaEnabled();
            self.setMfaEnabled(mfaEnabled);
            getJDA().getEventManager().handle(
                new SelfUpdateMFAEvent(
                    getJDA(), responseNumber,
                    wasMfaEnabled));
        }

        if (getJDA().getAccountType() == AccountType.CLIENT)
        {
            if (!Objects.equals(email, self.getEmail()))
            {
                String oldEmail = self.getEmail();
                self.setEmail(email);
                getJDA().getEventManager().handle(
                    new SelfUpdateEmailEvent(
                        getJDA(), responseNumber,
                        oldEmail));
            }

            if (mobile != null && mobile != self.isMobile())
            {
                boolean oldMobile = self.isMobile();
                self.setMobile(mobile);
                getJDA().getEventManager().handle(
                    new SelfUpdateMobileEvent(
                        getJDA(), responseNumber,
                        oldMobile));
            }

            if (nitro != null && nitro != self.isNitro())
            {
                boolean oldNitro = self.isNitro();
                self.setNitro(nitro);
                getJDA().getEventManager().handle(
                    new SelfUpdateNitroEvent(
                        getJDA(), responseNumber,
                        oldNitro));
            }

            if (!Objects.equals(phoneNumber, self.getPhoneNumber()))
            {
                String oldPhoneNumber = self.getPhoneNumber();
                self.setPhoneNumber(phoneNumber);
                getJDA().getEventManager().handle(
                    new SelfUpdatePhoneNumberEvent(
                        getJDA(), responseNumber,
                        oldPhoneNumber));
            }
        }
        return null;
    }
}
