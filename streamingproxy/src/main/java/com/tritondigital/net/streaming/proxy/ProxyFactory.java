package com.tritondigital.net.streaming.proxy;

import com.tritondigital.net.streaming.proxy.client.Client;
import com.tritondigital.net.streaming.proxy.client.http.HttpClient;
import com.tritondigital.net.streaming.proxy.dataprovider.raw.RawPacketProvider;
import com.tritondigital.net.streaming.proxy.dataprovider.rtp.RtpPacketProvider;
import com.tritondigital.net.streaming.proxy.dataprovider.rtp.RtpPacketProviderMpeg4Generic;
import com.tritondigital.net.streaming.proxy.decoder.StreamContainerDecoder;
import com.tritondigital.net.streaming.proxy.decoder.flv.FlvDecoder;
import com.tritondigital.net.streaming.proxy.server.http.HttpServer;
import com.tritondigital.net.streaming.proxy.server.rtsp.RtspServer;


/**
 * <p>This class simplifies the creation of a proxy with the most frequents combination of client and server types.
 * It takes care of creating all instances needed for the creation of a proxy and connecting them together.
 *
 * <p>Usage example:
 *  <pre>
 *      Proxy proxy = ProxyFactory.createRtspProxy(ProxyFactory.RtspServerType.MP4_LATM, metadataDecodedListener);
 *
 *      // Start using the proxy and playing
 *      mMediaPlayer.start(proxy.startAsync("http://myFlvUrl"));
 *
 *      // Optionally, but strongly recommended, make sure that the MediaPlayer.OnPreparedListener calls proxy.audioPlaybackDidStart() in
 *      // its 'onPrepared' implementation.
 *  </pre>
 */
public class ProxyFactory
{
    /**
     * Proceeds to the creation of an RTSP Proxy using the given client as a source and streaming payload of the given server type.
     *
     * @param metadataDecodedListener   The listener to be notified when new Meta Data is received / decoded.
     *
     * @return The created proxy or {@code null} if this combination is not supported.
     */
    public static Proxy createRtspProxy(StreamContainerDecoder.MetaDataDecodedListener metadataDecodedListener)
    {
        // All needed instances
        Client client;                                  // Uses the external server as a source
        StreamContainerDecoder streamContainerDecoder;  // Listener to client
        RtpPacketProvider dataProvider;                 // Listener to streamContainerDeccoder
        RtspServer server;                              // Uses packetProvider as a source

        // Create client and decoder
        client = new HttpClient();
        streamContainerDecoder = new FlvDecoder();

        // Create server and Data Provider
        server = new RtspServer();
        dataProvider = new RtpPacketProviderMpeg4Generic();

        // Finally create the proxy with the server and client!
        Proxy proxy = new Proxy();
        proxy.setClient(client);
        proxy.setStreamContainerDecoder(streamContainerDecoder);
        proxy.setDataProvider(dataProvider);
        proxy.setServer(server);

        // Connect metadata listener
        proxy.getStreamContainerDecoder().setMetaDataDecodedListener(metadataDecodedListener);

        return proxy;
    }

    /**
     * Proceeds to the creation of an HTTP Proxy using the given client as a source and streaming payload of the given server type.
     *
     * @param metadataDecodedListener   The listener to be notified when new Meta Data is received / decoded.
     *
     * @return The created proxy or {@code null} if this combination is not supported.
     */
    public static Proxy createHttpProxy(StreamContainerDecoder.MetaDataDecodedListener metadataDecodedListener)
    {
        // All needed instances
        Client client;                                  // Uses the external server as a source
        StreamContainerDecoder streamContainerDecoder;  // Listener to client
        RawPacketProvider dataProvider;                 // Listener to streamContainerDeccoder
        HttpServer server;                              // Uses packetProvider as a source

        // Create client and decoder
        client = new HttpClient();
        streamContainerDecoder  = new FlvDecoder();

        // Create server and Data Provider
        server = new HttpServer();
        dataProvider = new RawPacketProvider();

        // Finally create the proxy with the server and client!
        Proxy proxy = new Proxy();
        proxy.setClient(client);
        proxy.setStreamContainerDecoder(streamContainerDecoder);
        proxy.setDataProvider(dataProvider);
        proxy.setServer(server);

        // Connect metadata listener
        proxy.getStreamContainerDecoder().setMetaDataDecodedListener(metadataDecodedListener);

        return proxy;
    }
}
