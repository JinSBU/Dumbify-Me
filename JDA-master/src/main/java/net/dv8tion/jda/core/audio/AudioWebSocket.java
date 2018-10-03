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

package net.dv8tion.jda.core.audio;

import com.neovisionaries.ws.client.*;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.audio.hooks.ConnectionListener;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.events.ExceptionEvent;
import net.dv8tion.jda.core.managers.impl.AudioManagerImpl;
import net.dv8tion.jda.core.utils.JDALogger;
import net.dv8tion.jda.core.utils.MiscUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class AudioWebSocket extends WebSocketAdapter
{
    public static final Logger LOG = JDALogger.getLog(AudioWebSocket.class);
    public static final int DISCORD_SECRET_KEY_LENGTH = 32;
    private static final byte[] UDP_KEEP_ALIVE= { (byte) 0xC9, 0, 0, 0, 0, 0, 0, 0, 0 };

    protected volatile AudioEncryption encryption;
    protected WebSocket socket;

    private final AudioConnection audioConnection;
    private final ConnectionListener listener;
    private final ScheduledThreadPoolExecutor keepAlivePool;
    private final Guild guild;
    private final String sessionId;
    private final String token;
    private final String wssEndpoint;

    private volatile ConnectionStatus connectionStatus = ConnectionStatus.NOT_CONNECTED;
    private boolean ready = false;
    private boolean reconnecting = false;
    private boolean shouldReconnect;
    private int ssrc;
    private byte[] secretKey;
    private Future<?> keepAliveHandle;
    private InetSocketAddress address;

    private volatile boolean shutdown = false;

    protected AudioWebSocket(
            AudioConnection audioConnection, ConnectionListener listener, String endpoint, Guild guild,
            String sessionId, String token, boolean shouldReconnect)
    {
        this.audioConnection = audioConnection;
        this.listener = listener;
        this.guild = guild;
        this.sessionId = sessionId;
        this.token = token;
        this.shouldReconnect = shouldReconnect;

        keepAlivePool = getJDA().getAudioKeepAlivePool();

        //Append the Secure Websocket scheme so that our websocket library knows how to connect
        wssEndpoint = String.format("wss://%s/?v=%d", endpoint, JDAInfo.AUDIO_GATEWAY_VERSION);

        if (sessionId == null || sessionId.isEmpty())
            throw new IllegalArgumentException("Cannot create a voice connection using a null/empty sessionId!");
        if (token == null || token.isEmpty())
            throw new IllegalArgumentException("Cannot create a voice connection using a null/empty token!");
    }

    /* Used by AudioConnection */

    protected void send(String message)
    {
        LOG.trace("<- {}", message);
        socket.sendText(message);
    }

    protected void send(int op, Object data)
    {
        send(new JSONObject()
            .put("op", op)
            .put("d", data == null ? JSONObject.NULL : data)
            .toString());
    }

    protected void startConnection()
    {
        if (!reconnecting && socket != null)
            throw new IllegalStateException("Somehow, someway, this AudioWebSocket has already attempted to start a connection!");

        try
        {
            socket = getJDA().getWebSocketFactory()
                             .createSocket(wssEndpoint)
                             .addListener(this);
            changeStatus(ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT);
            socket.connectAsynchronously();
        }
        catch (IOException e)
        {
            LOG.warn("Encountered IOException while attempting to connect: {}\nClosing connection and attempting to reconnect.", e.getMessage());
            this.close(ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT);
        }
    }

    protected void close(final ConnectionStatus closeStatus)
    {
        //Makes sure we don't run this method again after the socket.close(1000) call fires onDisconnect
        if (shutdown)
            return;
        locked((manager) ->
        {
            if (shutdown)
                return;
            ConnectionStatus status = closeStatus;
            ready = false;
            shutdown = true;
            stopKeepAlive();

            if (audioConnection.udpSocket != null)
                audioConnection.udpSocket.close();
            if (socket != null)
                socket.sendClose();

            audioConnection.shutdown();

            VoiceChannel disconnectedChannel = manager.getConnectedChannel();
            if (disconnectedChannel == null)
                disconnectedChannel = manager.getQueuedAudioConnection();

            manager.setAudioConnection(null);

            //Verify that it is actually a lost of connection and not due the connected channel being deleted.
            JDAImpl api = getJDA();
            if (status == ConnectionStatus.ERROR_LOST_CONNECTION)
            {
                //Get guild from JDA, don't use [guild] field to make sure that we don't have
               // a problem of an out of date guild stored in [guild] during a possible mWS invalidate.
                Guild connGuild = api.getGuildById(guild.getIdLong());
                if (connGuild != null)
                {
                    if (connGuild.getVoiceChannelById(audioConnection.getChannel().getIdLong()) == null)
                        status = ConnectionStatus.DISCONNECTED_CHANNEL_DELETED;
                }
            }

            changeStatus(status);

            //decide if we reconnect.
            if (shouldReconnect
                    && status != ConnectionStatus.NOT_CONNECTED    //indicated that the connection was purposely closed. don't reconnect.
                    && status != ConnectionStatus.DISCONNECTED_CHANNEL_DELETED
                    && status != ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD
                    && status != ConnectionStatus.AUDIO_REGION_CHANGE) //Already handled.
            {
                manager.setQueuedAudioConnection(disconnectedChannel);
                api.getClient().queueAudioReconnect(disconnectedChannel);
            }
            else if (status != ConnectionStatus.AUDIO_REGION_CHANGE)
            {
                api.getClient().queueAudioDisconnect(guild);
            }
        });
    }

    protected void changeStatus(ConnectionStatus newStatus)
    {
        connectionStatus = newStatus;
        listener.onStatusChange(newStatus);
    }

    protected void setAutoReconnect(boolean shouldReconnect)
    {
        this.shouldReconnect = shouldReconnect;
    }

    protected ConnectionStatus getConnectionStatus()
    {
        return connectionStatus;
    }

    protected InetSocketAddress getAddress()
    {
        return address;
    }

    protected byte[] getSecretKey()
    {
        return secretKey;
    }

    protected int getSSRC()
    {
        return ssrc;
    }

    protected boolean isReady()
    {
        return ready;
    }

    /* TCP Listeners */

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread)
    {
        getJDA().setContext();
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers)
    {
        if (shutdown)
        {
            //Somehow this AudioWebSocket was shutdown before we finished connecting....
            // thus we just disconnect here since we were asked to shutdown
            socket.sendClose(1000);
            return;
        }

        if (reconnecting)
            resume();
        else
            identify();
        changeStatus(ConnectionStatus.CONNECTING_AWAITING_AUTHENTICATION);
        if (!reconnecting)
            audioConnection.prepareReady();
        reconnecting = false;
    }

    @Override
    public void onTextMessage(WebSocket websocket, String message)
    {
        try
        {
            handleEvent(new JSONObject(message));
        }
        catch (Exception ex)
        {
            LOG.error("Encountered exception trying to handle an event message: {}", message, ex);
        }
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer)
    {
        if (shutdown)
            return;
        LOG.debug("The Audio connection was closed!\nBy remote? {}", closedByServer);
        if (serverCloseFrame != null)
        {
            LOG.debug("Reason: {}\nClose code: {}", serverCloseFrame.getCloseReason(), serverCloseFrame.getCloseCode());
            final int code = serverCloseFrame.getCloseCode();
            final VoiceCode.Close closeCode = VoiceCode.Close.from(code);
            switch (closeCode)
            {
                case SERVER_NOT_FOUND:
                case SERVER_CRASH:
                case INVALID_SESSION:
                    this.close(ConnectionStatus.ERROR_CANNOT_RESUME);
                    break;
                case AUTHENTICATION_FAILED:
                    this.close(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE);
                    break;
                default:
                    this.reconnect();
            }
            return;
        }
        if (clientCloseFrame != null)
        {
            LOG.debug("ClientReason: {}\nClientCode: {}", clientCloseFrame.getCloseReason(), clientCloseFrame.getCloseCode());
            if (clientCloseFrame.getCloseCode() != 1000)
            {
                // unexpected close -> error -> attempt resume
                this.reconnect();
                return;
            }
        }
        this.close(ConnectionStatus.NOT_CONNECTED);
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause)
    {
        handleCallbackError(websocket, cause);
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause)
    {
        LOG.error("There was some audio websocket error", cause);
        JDAImpl api = getJDA();
        api.getEventManager().handle(new ExceptionEvent(api, cause, true));
    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread)
    {
        final String identifier = getJDA().getIdentifierString();
        final String guildId = guild.getId();
        switch (threadType)
        {
            case CONNECT_THREAD:
                thread.setName(identifier + " AudioWS-ConnectThread (guildId: " + guildId + ')');
                break;
            case FINISH_THREAD:
                thread.setName(identifier + " AudioWS-FinishThread (guildId: " + guildId + ')');
                break;
            case WRITING_THREAD:
                thread.setName(identifier + " AudioWS-WriteThread (guildId: " + guildId + ')');
                break;
            case READING_THREAD:
                thread.setName(identifier + " AudioWS-ReadThread (guildId: " + guildId + ')');
                break;
            default:
                thread.setName(identifier + " AudioWS-" + threadType + " (guildId: " + guildId + ')');
        }
    }

    @Override
    public void onConnectError(WebSocket webSocket, WebSocketException e)
    {
        LOG.warn("Failed to establish websocket connection: {} - {}\nClosing connection and attempting to reconnect.",
                                 e.getError(), e.getMessage());
        this.close(ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT);
    }

    /* Internals */

    private void handleEvent(JSONObject contentAll)
    {
        int opCode = contentAll.getInt("op");

        switch(opCode)
        {
            case VoiceCode.HELLO:
            {
                LOG.trace("-> HELLO {}", contentAll);
                final JSONObject payload = contentAll.getJSONObject("d");
                final int interval = payload.getInt("heartbeat_interval");
                stopKeepAlive();
                setupKeepAlive(interval);
                break;
            }
            case VoiceCode.READY:
            {
                LOG.trace("-> READY {}", contentAll);
                JSONObject content = contentAll.getJSONObject("d");
                ssrc = content.getInt("ssrc");
                int port = content.getInt("port");
                String ip = content.getString("ip");
                JSONArray modes = content.getJSONArray("modes");
                encryption = AudioEncryption.getPreferredMode(modes);
                if (encryption == null)
                {
                    close(ConnectionStatus.ERROR_UNSUPPORTED_ENCRYPTION_MODES);
                    LOG.error("None of the provided encryption modes are supported: {}", modes);
                    return;
                }
                else
                {
                    LOG.debug("Using encryption mode " + encryption.getKey());
                }

                //Find our external IP and Port using Discord
                InetSocketAddress externalIpAndPort;

                changeStatus(ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY);
                int tries = 0;
                do
                {
                    externalIpAndPort = handleUdpDiscovery(new InetSocketAddress(ip, port), ssrc);
                    tries++;
                    if (externalIpAndPort == null && tries > 5)
                    {
                        close(ConnectionStatus.ERROR_UDP_UNABLE_TO_CONNECT);
                        return;
                    }
                } while (externalIpAndPort == null);

                final JSONObject object = new JSONObject()
                        .put("protocol", "udp")
                        .put("data", new JSONObject()
                                .put("address", externalIpAndPort.getHostString())
                                .put("port", externalIpAndPort.getPort())
                                .put("mode", encryption.getKey())); //Discord requires encryption
                send(VoiceCode.SELECT_PROTOCOL, object);
                changeStatus(ConnectionStatus.CONNECTING_AWAITING_READY);
                break;
            }
            case VoiceCode.RESUMED:
            {
                LOG.trace("-> RESUMED {}", contentAll);
                LOG.debug("Successfully resumed session!");
                changeStatus(ConnectionStatus.CONNECTED);
                ready = true;
                break;
            }
            case VoiceCode.SESSION_DESCRIPTION:
            {
                LOG.trace("-> SESSION_DESCRIPTION {}", contentAll);
                //secret_key is an array of 32 ints that are less than 256, so they are bytes.
                JSONArray keyArray = contentAll.getJSONObject("d").getJSONArray("secret_key");

                secretKey = new byte[DISCORD_SECRET_KEY_LENGTH];
                for (int i = 0; i < keyArray.length(); i++)
                    secretKey[i] = (byte) keyArray.getInt(i);

                LOG.trace("Audio connection has finished connecting!");
                ready = true;
                changeStatus(ConnectionStatus.CONNECTED);
                break;
            }
            case VoiceCode.HEARTBEAT:
            {
                LOG.trace("-> HEARTBEAT {}", contentAll);
                send(VoiceCode.HEARTBEAT, System.currentTimeMillis());
                break;
            }
            case VoiceCode.HEARTBEAT_ACK:
            {
                LOG.trace("-> HEARTBEAT_ACK {}", contentAll);
                final long ping = System.currentTimeMillis() - contentAll.getLong("d");
                listener.onPing(ping);
                break;
            }
            case VoiceCode.USER_SPEAKING_UPDATE:
            {
                LOG.trace("-> USER_SPEAKING_UPDATE {}", contentAll);
                final JSONObject content = contentAll.getJSONObject("d");
                final EnumSet<SpeakingMode> speaking = SpeakingMode.getModes(content.getInt("speaking"));
                final int ssrc = content.getInt("ssrc");
                final long userId = content.getLong("user_id");

                final User user = getUser(userId);
                if (user == null)
                {
                    //more relevant for audio connection
                    LOG.trace("Got an Audio USER_SPEAKING_UPDATE for a non-existent User. JSON: {}", contentAll);
                    break;
                }

                audioConnection.updateUserSSRC(ssrc, userId);
                listener.onUserSpeaking(user, speaking);
                break;
            }
            case VoiceCode.USER_DISCONNECT:
            {
                LOG.trace("-> USER_DISCONNECT {}", contentAll);
                final JSONObject payload = contentAll.getJSONObject("d");
                final long userId = payload.getLong("user_id");
                audioConnection.removeUserSSRC(userId);
                break;
            }
            case 12:
            case 14:
            {
                LOG.trace("-> OP {} {}", opCode, contentAll);
                // ignore op 12 and 14 for now
                break;
            }
            default:
                LOG.debug("Unknown Audio OP code.\n{}", contentAll);
        }
    }

    private void identify()
    {
        JSONObject connectObj = new JSONObject()
                .put("server_id", guild.getId())
                .put("user_id", getJDA().getSelfUser().getId())
                .put("session_id", sessionId)
                .put("token", token);
        send(VoiceCode.IDENTIFY, connectObj);
    }

    private void resume()
    {
        LOG.debug("Sending resume payload...");
        JSONObject resumeObj = new JSONObject()
                .put("server_id", guild.getId())
                .put("session_id", sessionId)
                .put("token", token);
        send(VoiceCode.RESUME, resumeObj);
    }

    private JDAImpl getJDA()
    {
        return audioConnection.getJDA();
    }

    private void locked(Consumer<AudioManagerImpl> consumer)
    {
        AudioManagerImpl manager = (AudioManagerImpl) guild.getAudioManager();
        MiscUtil.locked(manager.CONNECTION_LOCK, () -> consumer.accept(manager));
    }

    private void reconnect()
    {
        if (shutdown)
            return;
        locked((unused) ->
        {
            if (shutdown)
                return;
            ready = false;
            reconnecting = true;
            changeStatus(ConnectionStatus.ERROR_LOST_CONNECTION);
            startConnection();
        });
    }

    private InetSocketAddress handleUdpDiscovery(InetSocketAddress address, int ssrc)
    {
        //We will now send a packet to discord to punch a port hole in the NAT wall.
        //This is called UDP hole punching.
        try
        {
            audioConnection.udpSocket = new DatagramSocket();   //Use UDP, not TCP.

            //Create a byte array of length 70 containing our ssrc.
            ByteBuffer buffer = ByteBuffer.allocate(70);    //70 taken from https://github.com/Rapptz/discord.py/blob/async/discord/voice_client.py#L208
            buffer.putInt(ssrc);                            //Put the ssrc that we were given into the packet to send back to discord.

            //Construct our packet to be sent loaded with the byte buffer we store the ssrc in.
            DatagramPacket discoveryPacket = new DatagramPacket(buffer.array(), buffer.array().length, address);
            audioConnection.udpSocket.send(discoveryPacket);

            //Discord responds to our packet, returning a packet containing our external ip and the port we connected through.
            DatagramPacket receivedPacket = new DatagramPacket(new byte[70], 70);   //Give a buffer the same size as the one we sent.
            audioConnection.udpSocket.setSoTimeout(1000);
            audioConnection.udpSocket.receive(receivedPacket);

            //The byte array returned by discord containing our external ip and the port that we used
            //to connect to discord with.
            byte[] received = receivedPacket.getData();

            //Example string:"   121.83.253.66                                                   ��"
            //You'll notice that there are 4 leading nulls and a large amount of nulls between the the ip and
            // the last 2 bytes. Not sure why these exist.  The last 2 bytes are the port. More info below.
            String ourIP = new String(receivedPacket.getData());//Puts the entire byte array in. nulls are converted to spaces.
            ourIP = ourIP.substring(4, ourIP.length() - 2); //Removes the SSRC of the answer package and the port that is stuck on the end of this string. (last 2 bytes are the port)
            ourIP = ourIP.trim();  //Removes the extra whitespace(nulls) attached to both sides of the IP

            //The port exists as the last 2 bytes in the packet data, and is encoded as an UNSIGNED short.
            //Furthermore, it is stored in Little Endian instead of normal Big Endian.
            //We will first need to convert the byte order from Little Endian to Big Endian (reverse the order)
            //Then we will need to deal with the fact that the bytes represent an unsigned short.
            //Java cannot deal with unsigned types, so we will have to promote the short to a higher type.
            //Options:  char or int.  I will be doing int because it is just easier to work with.
            byte[] portBytes = new byte[2];                 //The port is exactly 2 bytes in size.
            portBytes[0] = received[received.length - 1];   //Get the second byte and store as the first
            portBytes[1] = received[received.length - 2];   //Get the first byte and store as the second.
            //We have now effectively converted from Little Endian -> Big Endian by reversing the order.

            //For more information on how this is converting from an unsigned short to an int refer to:
            //http://www.darksleep.com/player/JavaAndUnsignedTypes.html
            int firstByte = (0x000000FF & ((int) portBytes[0]));    //Promotes to int and handles the fact that it was unsigned.
            int secondByte = (0x000000FF & ((int) portBytes[1]));   //

            //Combines the 2 bytes back together.
            int ourPort = (firstByte << 8) | secondByte;

            this.address = address;

            return new InetSocketAddress(ourIP, ourPort);
        }
        catch (SocketException e)
        {
            return null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private void stopKeepAlive()
    {
        if (keepAliveHandle != null)
            keepAliveHandle.cancel(true);
        keepAliveHandle = null;
    }

    private void setupKeepAlive(final int keepAliveInterval)
    {
        if (keepAliveHandle != null)
            LOG.error("Setting up a KeepAlive runnable while the previous one seems to still be active!!");

        Runnable keepAliveRunnable = () ->
        {
            if (socket != null && socket.isOpen()) //TCP keep-alive
                send(VoiceCode.HEARTBEAT, System.currentTimeMillis());
            if (audioConnection.udpSocket != null && !audioConnection.udpSocket.isClosed()) //UDP keep-alive
            {
                try
                {
                    DatagramPacket keepAlivePacket = new DatagramPacket(UDP_KEEP_ALIVE, UDP_KEEP_ALIVE.length, address);
                    audioConnection.udpSocket.send(keepAlivePacket);
                }
                catch (NoRouteToHostException e)
                {
                    LOG.warn("Closing AudioConnection due to inability to ping audio packets.");
                    LOG.warn("Cannot send audio packet because JDA navigate the route to Discord.\n" +
                            "Are you sure you have internet connection? It is likely that you've lost connection.");
                    this.close(ConnectionStatus.ERROR_LOST_CONNECTION);
                }
                catch (IOException e)
                {
                    LOG.error("There was some error sending an audio keepalive packet", e);
                }
            }
        };

        try
        {
            keepAliveHandle = keepAlivePool.scheduleAtFixedRate(keepAliveRunnable, 0, keepAliveInterval, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException ignored) {} //ignored because this is probably caused due to a race condition
                                                      // related to the threadpool shutdown.
    }

    private User getUser(final long userId)
    {
        JDAImpl api = getJDA();
        User user = api.getUserById(userId);
        if (user != null)
            return user;
        return api.getFakeUserMap().get(userId);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize()
    {
        if (!shutdown)
        {
            LOG.error("Finalization hook of AudioWebSocket was triggered without properly shutting down");
            close(ConnectionStatus.NOT_CONNECTED);
        }
    }
}
