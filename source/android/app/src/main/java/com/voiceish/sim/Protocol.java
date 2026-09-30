package com.voiceish.sim;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.json.JSONObject;

final class Protocol {
    private final byte[] key;
    private final SmsEngine engine;
    private final SecureRandom random = new SecureRandom();
    Protocol(byte[] key, SmsEngine engine) { this.key = key.clone(); this.engine = engine; }
    static boolean isUuid(String value) { return value != null && value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); }
    byte[] handle(byte[] bytes) throws Exception {
        JSONObject envelope = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (envelope.getInt("v") != 1) throw new SecurityException("Unsupported version");
        byte[] iv = Base64.getDecoder().decode(envelope.getString("iv"));
        byte[] ciphertext = Base64.getDecoder().decode(envelope.getString("data"));
        byte[] clear = Crypto.decrypt(key, iv, ciphertext, "request");
        JSONObject request = new JSONObject(new String(clear, StandardCharsets.UTF_8));
        String id = request.getString("id");
        if (!isUuid(id)) throw new SecurityException("Invalid request ID");
        long timestamp = request.getLong("ts");
        long now = System.currentTimeMillis();
        JSONObject response = new JSONObject().put("id", id);
        try {
            if (timestamp < now - 90000 || timestamp > now + 90000)
                throw new BridgeException("CLOCK_SKEW", "Phone and computer clocks must be within 90 seconds. Enable automatic date and time on both.");
            engine.rememberRequest(id, now);
            JSONObject params = request.optJSONObject("params");
            if (params == null) throw new BridgeException("INVALID_PARAMS", "Request parameters must be an object.");
            JSONObject data;
            switch (request.optString("action", "")) {
                case "status": data = engine.status(); break;
                case "messages": data = engine.messages(params); break;
                case "send": data = engine.send(id, params); break;
                default: throw new BridgeException("UNKNOWN_ACTION", "This operation is not supported.");
            }
            response.put("ok", true).put("data", data);
        } catch (BridgeException error) {
            response.put("ok", false).put("error", new JSONObject().put("code", error.code).put("message", error.getMessage()));
        } catch (SecurityException error) {
            response.put("ok", false).put("error", new JSONObject().put("code", "PERMISSION_DENIED").put("message", "A required Android permission is unavailable. Check Voiceish SIM on your phone."));
        } catch (Exception error) {
            response.put("ok", false).put("error", new JSONObject().put("code", "INTERNAL_ERROR").put("message", "The phone could not complete this request. If sending, check the inbox before sending again."));
        }
        byte[] responseIv = new byte[12]; random.nextBytes(responseIv);
        byte[] sealed = Crypto.encrypt(key, responseIv, response.toString().getBytes(StandardCharsets.UTF_8), "response");
        return new JSONObject().put("v", 1).put("iv", Base64.getEncoder().encodeToString(responseIv))
            .put("data", Base64.getEncoder().encodeToString(sealed)).toString().getBytes(StandardCharsets.UTF_8);
    }
}
