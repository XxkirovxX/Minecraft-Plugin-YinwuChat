package org.lintx.plugins.yinwuchat.velocity.httpserver;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import org.lintx.plugins.yinwuchat.common.auth.AuthService;

/**
 * HTTP request handler for Velocity
 * Serves static web files and handles HTTP requests
 */
public class VelocityHttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final File rootFolder;
    private final AuthService authService;

    public VelocityHttpRequestHandler(File rootFolder) {
        this.rootFolder = rootFolder;
        File dataFolder = rootFolder.getParentFile();
        this.authService = AuthService.getInstance(dataFolder);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String uri = request.uri();
        if (uri.equals("/") || uri.equals("")) {
            uri = "/index.html";
        }

        if (uri.equals("/api/wsinfo")) {
            sendJson(ctx, request, buildWsInfoJson(request));
            return;
        }

        if (uri.startsWith("/api/auth/")) {
            handleAuthApi(ctx, request, uri);
            return;
        }

        if (request.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        // Prevent directory traversal
        if (uri.contains("..")) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        File file = new File(rootFolder, uri);
        if (!file.exists() || file.isDirectory()) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        // Serve static file
        serveFile(ctx, file);
    }

    private void serveFile(ChannelHandlerContext ctx, File file) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        long fileLength = raf.length();

        String mime = getMimeType(file.getName());
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders heads = response.headers();
        heads.add(HttpHeaderNames.CONTENT_TYPE, mime + "; charset=UTF-8");
        heads.add(HttpHeaderNames.CONTENT_LENGTH, fileLength);
        heads.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        ctx.write(response);
        ctx.write(new ChunkedFile(raf, 0, fileLength, 8192), ctx.newPromise());
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    private void handleAuthApi(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        if (request.method() == HttpMethod.OPTIONS) {
            sendCorsPreflight(ctx, request);
            return;
        }
        if (request.method() == HttpMethod.GET) {
            if (path.equals("/api/auth/handshake")) {
                sendJson(ctx, request, authService.toJson(authService.createHandshakeResponse()));
                return;
            }
            if (path.equals("/api/auth/captcha")) {
                sendJson(ctx, request, authService.toJson(authService.createCaptchaResponse()));
                return;
            }
            if (path.startsWith("/api/auth/reset/status/")) {
                String username = path.substring("/api/auth/reset/status/".length());
                sendJson(ctx, request, authService.toJson(authService.handleCheckResetStatus(username)));
                return;
            }
        }
        if (request.method() == HttpMethod.POST) {
            String body = request.content().toString(StandardCharsets.UTF_8);
            if (path.equals("/api/auth/register")) {
                sendJson(ctx, request, authService.toJson(authService.handleRegister(body)));
                return;
            }
            if (path.equals("/api/auth/login")) {
                sendJson(ctx, request, authService.toJson(authService.handleLogin(body)));
                return;
            }
            if (path.equals("/api/auth/delete")) {
                sendJson(ctx, request, authService.toJson(authService.handleDeleteAccount(body, playerName -> {
                    // Velocity 平台的 Token 删除逻辑
                    java.util.UUID uuid = org.lintx.plugins.yinwuchat.velocity.config.PlayerConfig.getInstance().getTokenManager().getUuidByName(playerName);
                    if (uuid != null) {
                        org.lintx.plugins.yinwuchat.velocity.config.PlayerConfig.getInstance().getTokenManager().removeUuid(uuid);
                    }
                })));
                return;
            }
            if (path.equals("/api/auth/reset/request")) {
                sendJson(ctx, request, authService.toJson(authService.handleRequestReset(body, (accountName, playerName) -> {
                    // Velocity 平台的 绑定验证逻辑
                    java.util.UUID uuid = org.lintx.plugins.yinwuchat.velocity.config.PlayerConfig.getInstance().getTokenManager().getUuidByName(playerName);
                    if (uuid == null) return false;
                    String token = org.lintx.plugins.yinwuchat.velocity.config.PlayerConfig.getInstance().getTokenManager().getToken(uuid);
                    // 只要该玩家名绑定了任意有效的 Token，且账户名为 accountName (在此系统中账户名即 Token 关联的名字)
                    // 由于目前系统中 accountName 和 playerName 是通过 Token 关联的，
                    // 我们只需确认该 playerName 是否有绑定的 Token 即可。
                    return token != null;
                })));
                return;
            }
            if (path.equals("/api/auth/reset/submit")) {
                sendJson(ctx, request, authService.toJson(authService.handleResetPassword(body)));
                return;
            }
        }
        sendErrorWithCors(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED);
    }

    private void sendJson(ChannelHandlerContext ctx, FullHttpRequest request, String json) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(json, StandardCharsets.UTF_8)
        );
        HttpHeaders heads = response.headers();
        heads.add(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        heads.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        addCorsHeaders(heads, request);
        ctx.writeAndFlush(response);
    }

    private void sendCorsPreflight(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.NO_CONTENT
        );
        addCorsHeaders(response.headers(), request);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response);
    }

    private void addCorsHeaders(HttpHeaders headers, FullHttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin != null ? origin : "*");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "86400");
    }

    private String buildWsInfoJson(FullHttpRequest request) {
        String host = request.headers().get(HttpHeaderNames.HOST);
        if (host == null || host.isEmpty()) host = "localhost";
        if (host.contains(":")) host = host.split(":")[0];
        String proto = "ws";
        String forwardedProto = request.headers().get("X-Forwarded-Proto");
        if ("https".equalsIgnoreCase(forwardedProto)) proto = "wss";
        int port = org.lintx.plugins.yinwuchat.velocity.YinwuChat.getInstance().getConfig().wsport;
        String wsUrl = proto + "://" + host + ":" + port + "/ws";
        String json = "{\"ok\":true,\"wsPort\":" + port + ",\"wsPath\":\"/ws\",\"wsUrl\":\"" + wsUrl + "\"}";
        return json;
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, 
            status,
            Unpooled.copiedBuffer(status.toString(), StandardCharsets.UTF_8)
        );
        HttpHeaders heads = response.headers();
        heads.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        heads.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        
        ChannelFuture future = ctx.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    private void sendErrorWithCors(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            Unpooled.copiedBuffer(status.toString(), StandardCharsets.UTF_8)
        );
        HttpHeaders heads = response.headers();
        heads.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        heads.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        addCorsHeaders(heads, request);
        ChannelFuture future = ctx.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    private String getMimeType(String filename) {
        if (filename.endsWith(".html") || filename.endsWith(".htm")) {
            return "text/html";
        } else if (filename.endsWith(".css")) {
            return "text/css";
        } else if (filename.endsWith(".js")) {
            return "application/javascript";
        } else if (filename.endsWith(".json")) {
            return "application/json";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        } else if (filename.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (filename.endsWith(".woff") || filename.endsWith(".woff2")) {
            return "font/woff";
        } else {
            return "application/octet-stream";
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
