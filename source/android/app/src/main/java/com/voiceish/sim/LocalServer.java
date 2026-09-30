package com.voiceish.sim;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class LocalServer implements AutoCloseable {
    private final Protocol protocol;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8));
    private volatile boolean active;
    private ServerSocket server;
    LocalServer(Protocol protocol) { this.protocol = protocol; }
    void start() throws IOException {
        server = new ServerSocket(); server.setReuseAddress(true); server.bind(new java.net.InetSocketAddress(8765), 12);
        active = true;
        Thread listener = new Thread(this::accept, "voiceish-local-listener"); listener.setDaemon(true); listener.start();
    }
    private void accept() {
        while (active) {
            try {
                Socket socket = server.accept();
                if (!isLocal(socket.getInetAddress())) { socket.close(); continue; }
                socket.setSoTimeout(5000); clients.add(socket);
                try { executor.execute(() -> { try { serve(socket); } finally { clients.remove(socket); try { socket.close(); } catch (IOException ignored) { } } }); }
                catch (RejectedExecutionException rejected) { clients.remove(socket); socket.close(); }
            } catch (IOException error) {
                if (active) { BridgeService.lastError = "The network listener stopped. Stop and restart the bridge."; active = false; }
            }
        }
    }
    static boolean isLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
            || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc);
    }
    private void serve(Socket socket) {
        try {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            String first = line(input, 2048);
            String[] request = first.split(" ");
            if (request.length != 3 || !("HTTP/1.1".equals(request[2]) || "HTTP/1.0".equals(request[2]))) { respond(output, 400, null); return; }
            Map<String, String> headers = new HashMap<>(); int size = first.length() + 2;
            while (true) {
                String line = line(input, 8192); size += line.length() + 2;
                if (size > 16384) { respond(output, 413, null); return; }
                if (line.isEmpty()) break;
                int colon = line.indexOf(':');
                if (colon < 1) { respond(output, 400, null); return; }
                String name = line.substring(0, colon).trim().toLowerCase(Locale.US);
                String value = line.substring(colon + 1).trim();
                if (headers.put(name, value) != null) { respond(output, 400, null); return; }
            }
            if (!"/v1".equals(request[1])) { respond(output, 404, null); return; }
            if ("OPTIONS".equals(request[0])) { respond(output, 204, null); return; }
            if (!"POST".equals(request[0])) { respond(output, 405, null); return; }
            if (headers.containsKey("transfer-encoding") || !headers.getOrDefault("content-type", "").toLowerCase(Locale.US).startsWith("application/json")) { respond(output, 400, null); return; }
            int length;
            try { length = Integer.parseInt(headers.getOrDefault("content-length", "-1")); }
            catch (NumberFormatException invalid) { respond(output, 400, null); return; }
            if (length > 65536 || (long) length + size > 65536) { respond(output, 413, null); return; }
            if (length <= 0) { respond(output, 400, null); return; }
            byte[] bytes = new byte[length]; int read = 0;
            while (read < length) { int next = input.read(bytes, read, length - read); if (next < 0) throw new IOException("Incomplete request"); read += next; }
            try { respond(output, 200, protocol.handle(bytes)); }
            catch (Exception invalid) { respond(output, 401, null); }
        } catch (IOException ignored) { /* Do not log headers, bodies, phone numbers, or key material. */ }
    }
    private static String line(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (out.size() <= maximum) {
            int next = input.read(); if (next < 0) throw new IOException("Incomplete header");
            if (next == '\n') {
                byte[] raw = out.toByteArray();
                if (raw.length < 1 || raw[raw.length - 1] != '\r') throw new IOException("Invalid HTTP line");
                return new String(raw, 0, raw.length - 1, StandardCharsets.US_ASCII);
            }
            out.write(next);
        }
        throw new IOException("Header too large");
    }
    private static void respond(OutputStream output, int code, byte[] body) throws IOException {
        if (body == null) body = new byte[0];
        String reason = code == 200 ? "OK" : code == 204 ? "No Content" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : code == 405 ? "Method Not Allowed" : code == 413 ? "Payload Too Large" : "Bad Request";
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length
            + "\r\nConnection: close\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: POST, OPTIONS"
            + "\r\nAccess-Control-Allow-Headers: Content-Type\r\nAccess-Control-Allow-Private-Network: true\r\nAccess-Control-Max-Age: 600\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII)); output.write(body); output.flush();
    }
    @Override public void close() {
        active = false;
        if (server != null) try { server.close(); } catch (IOException ignored) { }
        for (Socket socket : clients) try { socket.close(); } catch (IOException ignored) { }
        clients.clear(); executor.shutdownNow();
    }
}
