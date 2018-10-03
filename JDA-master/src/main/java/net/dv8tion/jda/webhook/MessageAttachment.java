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

package net.dv8tion.jda.webhook;

import net.dv8tion.jda.core.utils.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/* package-private */ class MessageAttachment
{
    final String name;
    final byte[] data;

    MessageAttachment(String name, byte[] data)
    {
        this.name = name;
        this.data = data;
    }

    MessageAttachment(String name, InputStream stream) throws IOException
    {
        this.name = name;
        this.data = IOUtil.readFully(stream);
    }

    MessageAttachment(String name, File file) throws IOException
    {
        this.name = name;
        this.data = IOUtil.readFully(file);
    }

    InputStream getData()
    {
        return new ByteArrayInputStream(data);
    }
}
