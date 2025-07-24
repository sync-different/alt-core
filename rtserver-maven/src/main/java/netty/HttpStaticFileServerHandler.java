package netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SystemPropertyUtil;

import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.stream.ChunkedNioStream;

import jakarta.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ParseException;
import utils.WebFuncs;

/**
 * A simple handler that serves incoming HTTP requests to send their respective
 * HTTP responses.  It also implements {@code 'If-Modified-Since'} header to
 * take advantage of browser cache, as described in
 * <a href="http://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 *
 * <h3>How Browser Caching Works</h3>
 *
 * Web browser caching works with HTTP headers as illustrated by the following
 * sample:
 * <ol>
 * <li>Request #1 returns the content of {@code /file1.txt}.</li>
 * <li>Contents of {@code /file1.txt} is cached by the browser.</li>
 * <li>Request #2 for {@code /file1.txt} does return the contents of the
 *     file again. Rather, a 304 Not Modified is returned. This tells the
 *     browser to use the contents stored in its cache.</li>
 * <li>The server knows the file has not been modified because the
 *     {@code If-Modified-Since} date is the same as the file's last
 *     modified date.</li>
 * </ol>
 *
 * <pre>
 * Request #1 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 *
 * Response #1 Headers
 * ===================
 * HTTP/1.1 200 OK
 * Date:               Tue, 01 Mar 2011 22:44:26 GMT
 * Last-Modified:      Wed, 30 Jun 2010 21:36:48 GMT
 * Expires:            Tue, 01 Mar 2012 22:44:26 GMT
 * Cache-Control:      private, max-age=31536000
 *
 * Request #2 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 * If-Modified-Since:  Wed, 30 Jun 2010 21:36:48 GMT
 *
 * Response #2 Headers
 * ===================
 * HTTP/1.1 304 Not Modified
 * Date:               Tue, 01 Mar 2011 22:44:28 GMT
 *
 * </pre>
 */



public class HttpStaticFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        if (request.method() != GET) {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        String uri = request.uri();
        
        String queryString = null;
        if(uri.contains(".fn"))
          queryString = uri.split(".fn\\?")[1];
        if(uri.contains(".m3u8"))
          queryString = uri.split(".m3u8\\?")[1];
        
        
        String[] tokens = queryString.split("&");  
        Map<String, String> map = new HashMap<String, String>();  
        for (String param : tokens)  
        {  
            String name = param.split("=")[0];  
            String value = param.split("=")[1];  
            map.put(name, value);  
        }
        
        String uuid = map.get("uuid");
        
        boolean loggedin = checkIfLoggedIn(uuid);
        if(!loggedin){
            
            sendError(ctx, UNAUTHORIZED);
            return;
        }
            
        boolean m3u8 = false;
        if(uri.contains("getvideo.fn")){
            String md5requested = map.get("md5");
            uri = "/streaming/" + md5requested + "/OUTPUT.m3u8";
            m3u8 = true;
        }
        
        
        if(uri.contains("getvideo.m3u8")){
            String md5requested = map.get("md5");
            uri = "/streaming/" + md5requested + "/OUTPUT.m3u8";
            m3u8 = true;
        }

        if(uri.contains("getts.fn")){         
            String md5requested = map.get("md5");
            String tsrequested = map.get("ts");
            
            uri = "/streaming/" + md5requested + "/" + tsrequested;
        }
        
        String path = sanitizeUri(uri);

        
        if(uri.contains("getaudio.fn")){         
            String md5requested = map.get("md5");
            
            String sFileName = null;
            String sMD5 = null;
            String hostNPort = request.headers().get(HOST);
            String host;
            if(hostNPort.contains(":")){
                host = hostNPort.split(":")[0];
            }else{
                host = hostNPort;
            }
            String LocalIP = host;
            String sPort = null;
            boolean bCloudHosted =  false;
            String ClientIP = host;
            String dbmode = "p2p";

            WebFuncs wf = new utils.WebFuncs("");

            String sPath = wf.openfile_mobile(md5requested, sFileName, sMD5, LocalIP, sPort, bCloudHosted, ClientIP, dbmode);
            path = URLDecoder.decode(sPath, "UTF-8");

        }

        
        
        
        


        
        System.out.println(path);
        if (path == null) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            sendError(ctx, NOT_FOUND);
            return;
        }

        if (file.isDirectory()) {
            if (uri.endsWith("/")) {
                sendListing(ctx, file);
            } else {
                sendRedirect(ctx, uri + '/');
            }
            return;
        }

        if (!file.isFile()) {
            sendError(ctx, FORBIDDEN);
            return;
        }
        
        
        if(m3u8){
            
            File tmp = File.createTempFile("tmp", uuid + ".m3u8");
            tmp.getCanonicalPath();
            byte[] encoded = Files.readAllBytes(Paths.get(file.getCanonicalPath()));
            String body = new String(encoded, "UTF-8");
  
            body = body.replaceAll("\\.ts", ".ts&uuid=" + uuid);
            body = body.replaceAll("\\/getts.fn", "/cass/getts.fn");

            tmp.deleteOnExit();

            BufferedWriter out = new BufferedWriter(new FileWriter(tmp));
            out.write(body);
            out.close();
            
            file = tmp;
            
        }

        // Cache Validation
        String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException ignore) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpHeaders.setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpHeaders.isKeepAlive(request)) {
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        response.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
        response.headers().set(Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                
        // Write the initial line and the header.
        ctx.write(response);
        
        byte[] content = new byte[(int)raf.length()];
        FileInputStream is = new FileInputStream(file);
        int n = 0;
        int nbytestotal = 0;
        while ((n = is.read(content)) > 0) {
                nbytestotal = nbytestotal + n;
        }

        System.out.println("nbytestotal:" + nbytestotal);
        
        final ReadableByteChannel aIn = java.nio.channels.Channels.newChannel(new ByteArrayInputStream(content));
	ChannelFuture writeFuture = ctx.write(new ChunkedNioStream(aIn), ctx.newProgressivePromise());
        
        // Write the content.
//        ChannelFuture sendFileFuture;
//        if (ctx.pipeline().get(SslHandler.class) == null) {
//            sendFileFuture =
//                    ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
//        } else {
//            sendFileFuture =
//                    ctx.write(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
//                            ctx.newProgressivePromise());
//        }

        writeFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                if (total < 0) { // total unknown
                    System.err.println(future.channel() + " Transfer progress: " + progress);
                } else {
                    System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
                }
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) {
                System.err.println(future.channel() + " Transfer complete.");
            }
        });

        // Write the end marker
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

        // Decide whether to close the connection or not.
        if (!HttpHeaders.isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private boolean checkIfLoggedIn(String uuid) {
        try {
            String sMode = GetConfig("mode", "../scrubber/config/" + "www-rtbackup.properties");
            if(sMode.equals("server")){
                Object user = HttpStaticFileServer.uuidmap.get(uuid);
                if(user != null)
                    return true;
            }else{
                //client
                String serverip = GetConfig("serverip", "../scrubber/config/" + "serverinfo.properties");
                String serverport = GetConfig("serverport", "../scrubber/config/" + "serverinfo.properties");
                //checkuseruuid.fn
                String url = String.format("http://%s:%s/cass/checkuseruuid.fn?uuid=%s",
                        serverip, serverport, uuid);
                CloseableHttpResponse response = httpRequest(url);
                if (response != null) {
                    try {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            String user = EntityUtils.toString(entity);
                            if (user != null && !user.isEmpty()) {
                                return true;
                            }
                        }
                    } catch (ParseException | IOException e) {
                        // Handle parsing error
                    } finally {
                        try {
                            response.close();
                        } catch (IOException e) {
                            // Ignore close error
                        }
                    }
                }

            }
        } catch (Exception ex) {
            Logger.getLogger(HttpStaticFileServerHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }

        if (!uri.startsWith("/")) {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
            uri.contains('.' + File.separator) ||
            uri.startsWith(".") || uri.endsWith(".") ||
            INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        return SystemPropertyUtil.get("user.dir") + File.separator + uri;
    }

    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    private static void sendListing(ChannelHandlerContext ctx, File dir) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");

        StringBuilder buf = new StringBuilder();
        String dirPath = dir.getPath();

        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><title>");
        buf.append("Listing of: ");
        buf.append(dirPath);
        buf.append("</title></head><body>\r\n");

        buf.append("<h3>Listing of: ");
        buf.append(dirPath);
        buf.append("</h3>\r\n");

        buf.append("<ul>");
        buf.append("<li><a href=\"../\">..</a></li>\r\n");

        for (File f: dir.listFiles()) {
            if (f.isHidden() || !f.canRead()) {
                continue;
            }

            String name = f.getName();
            if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
                continue;
            }

            buf.append("<li><a href=\"");
            buf.append(name);
            buf.append("\">");
            buf.append(name);
            buf.append("</a></li>\r\n");
        }

        buf.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
        response.headers().set(LOCATION, newUri);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     *
     * @param ctx
     *            Context
     */
    private static void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param response
     *            HTTP response
     */
    private static void setDateHeader(FullHttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        //response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
        String sType = "";
        if (file.getName().toLowerCase().contains(".mp3")) sType = "audio/mpeg";
        if (file.getName().toLowerCase().contains(".wma")) sType = "audio/x-ms-wma";
        if (file.getName().toLowerCase().contains(".m4a")) sType = "audio/mp4a-latm";
        if (file.getName().toLowerCase().contains(".mov")) sType = "video/quicktime";
        if (file.getName().toLowerCase().contains(".ts")) sType = "video/MP2T";
        if (file.getName().toLowerCase().contains(".m3u8")) sType = "application/x-mpegURL";
        response.headers().set(CONTENT_TYPE, sType);
    }
    
    String GetConfig(String _name, String _config) {
        
        try {
            File f = new File(_config);
            if (f.exists()) {
                Properties props = new Properties();
                InputStream is = new BufferedInputStream(new FileInputStream(f));
                props.load(is);
                is.close();
                String r = props.getProperty(_name);
                if (r != null ) {
                    //p("Old value = " + r);   
                    return r;
                } else {
                    return "";
                }
            } else {
                //p("File not found. exiting...");
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }              
    }
    
    private CloseableHttpResponse httpRequest(String url) {
        HttpGet request = new HttpGet(url);

        try {
            CloseableHttpClient httpclient = HttpClients.createDefault();
            CloseableHttpResponse response = httpclient.execute(request);
            int status = response.getCode();
            if (status == 200) {
                return response;
            }
        } catch (IOException e) {
            //log("Local http request failed with IOException");
            //e.printStackTrace(log);
        }
        return null;
    }


}