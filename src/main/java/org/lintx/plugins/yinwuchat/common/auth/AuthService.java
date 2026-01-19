package org.lintx.plugins.yinwuchat.common.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import java.security.spec.MGF1ParameterSpec;

public class AuthService {
    private static final Map<String, AuthService> INSTANCES = new HashMap<>();
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 16;

    private final AuthUserStore userStore;
    private final CaptchaManager captchaManager;
    private final HandshakeManager handshakeManager;
    private final ResetManager resetManager;
    private final KeyPair keyPair;
    private final String rsaOaepHash;
    private final String rsaFallbackHash;
    private final Gson gson = new Gson();

    private AuthService(File dataFolder) {
        if (dataFolder != null && !dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.userStore = new AuthUserStore(dataFolder);
        this.captchaManager = new CaptchaManager();
        this.handshakeManager = new HandshakeManager();
        this.resetManager = new ResetManager();
        this.keyPair = CryptoUtil.generateRsaKeyPair();
        String chosenHash = "SHA-256";
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
            javax.crypto.spec.OAEPParameterSpec spec = new javax.crypto.spec.OAEPParameterSpec(
                    chosenHash,
                    "MGF1",
                    new MGF1ParameterSpec(chosenHash),
                    javax.crypto.spec.PSource.PSpecified.DEFAULT
            );
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keyPair.getPrivate(), spec);
        } catch (Exception e) {
            chosenHash = "SHA-1";
        }
        this.rsaOaepHash = chosenHash;
        this.rsaFallbackHash = chosenHash.equals("SHA-256") ? "SHA-1" : "SHA-256";
    }

    public static synchronized AuthService getInstance(File dataFolder) {
        String key = dataFolder == null ? "default" : dataFolder.getAbsolutePath();
        return INSTANCES.computeIfAbsent(key, k -> new AuthService(dataFolder));
    }

    public JsonObject createHandshakeResponse() {
        HandshakeManager.Handshake handshake = handshakeManager.createHandshake();
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("handshakeId", handshake.id);
        json.addProperty("publicKey", CryptoUtil.publicKeyToPem(keyPair.getPublic()));
        json.addProperty("nonce", handshake.nonce);
        json.addProperty("hmacKey", Base64.getEncoder().encodeToString(handshake.hmacKey));
        json.addProperty("oaepHash", rsaOaepHash);
        json.addProperty("expiresIn", handshakeManager.getTtlMillis());
        return json;
    }

    public JsonObject createCaptchaResponse() {
        CaptchaManager.CaptchaResponse captcha = captchaManager.createCaptcha();
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("captchaId", captcha.id);
        json.addProperty("image", captcha.imageBase64);
        return json;
    }

    public String toJson(JsonObject object) {
        return gson.toJson(object);
    }

    public JsonObject handleRegister(String body) {
        try {
            JsonObject request = parseBody(body);
            AuthRequest auth = decryptAndVerify(request);
            if (auth == null) {
                return error("鉴权失败，请刷新后重试");
            }
            if (!captchaManager.validate(auth.captchaId, auth.captchaText)) {
                return error("验证码不正确或已过期");
            }
            if (!isValidUsername(auth.username)) {
                return error("用户名格式不正确，请使用 3-16 位字母数字下划线");
            }
            if (!isValidPasswordHash(auth.passwordHash)) {
                return error("密码格式不正确");
            }
            if (!userStore.register(auth.username, auth.passwordHash)) {
                return error("该用户名已被注册");
            }
            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            res.addProperty("message", "注册成功");
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return error("注册失败: " + safeErrorMessage(e));
        }
    }

    public JsonObject handleLogin(String body) {
        try {
            JsonObject request = parseBody(body);
            AuthRequest auth = decryptAndVerify(request);
            if (auth == null) {
                return error("鉴权失败，请刷新后重试");
            }
            if (!isValidUsername(auth.username)) {
                return error("用户名或密码错误");
            }
            if (!isValidPasswordHash(auth.passwordHash)) {
                return error("用户名或密码错误");
            }
            if (!userStore.verify(auth.username, auth.passwordHash)) {
                return error("用户名或密码错误");
            }
            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            res.addProperty("message", "登录成功");
            res.addProperty("username", userStore.getDisplayName(auth.username));
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return error("登录失败: " + safeErrorMessage(e));
        }
    }

    public JsonObject handleDeleteAccount(String body, TokenDeleter tokenDeleter) {
        try {
            JsonObject request = parseBody(body);
            AuthRequest auth = decryptAndVerify(request);
            if (auth == null) {
                return error("鉴权失败，请刷新后重试");
            }
            if (!userStore.verify(auth.username, auth.passwordHash)) {
                return error("用户名或密码错误");
            }
            
            // 账户删除
            boolean deleted = userStore.delete(auth.username);
            if (!deleted) {
                return error("账户不存在");
            }
            
            // Token 删除 (由具体平台实现)
            if (tokenDeleter != null && auth.playerName != null && !auth.playerName.isEmpty()) {
                tokenDeleter.deleteToken(auth.playerName);
            }
            
            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            res.addProperty("message", "账户注销成功，对应 Token 已失效");
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return error("注销失败: " + safeErrorMessage(e));
        }
    }

    public JsonObject handleRequestReset(String body, PlayerVerifier playerVerifier) {
        try {
            JsonObject request = parseBody(body);
            AuthRequest auth = decryptAndVerify(request);
            if (auth == null) {
                return error("鉴权失败，请刷新后重试");
            }
            if (!userStore.exists(auth.username)) {
                return error("该 Web 账户名不存在");
            }
            if (auth.playerName == null || auth.playerName.isEmpty()) {
                return error("请提供绑定的 Minecraft 玩家名");
            }
            
            // 验证该玩家是否确实绑定了该 Web 账户 (即玩家是否有该账户名关联的 Token)
            if (playerVerifier != null && !playerVerifier.isBound(auth.username, auth.playerName)) {
                return error("该玩家名与账户不匹配，或未在游戏中完成绑定");
            }
            
            // 生成重置码
            String code = resetManager.createSession(auth.username, auth.playerName);
            
            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            res.addProperty("code", code);
            res.addProperty("message", "重置申请已提交，请进入游戏完成验证");
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return error("重置申请失败: " + safeErrorMessage(e));
        }
    }

    public JsonObject handleCheckResetStatus(String username) {
        ResetManager.ResetSession session = resetManager.getSession(username);
        JsonObject res = new JsonObject();
        if (session == null) {
            res.addProperty("ok", false);
            res.addProperty("message", "重置会话已过期或不存在");
        } else {
            res.addProperty("ok", true);
            res.addProperty("verified", session.verified);
        }
        return res;
    }

    public JsonObject handleResetPassword(String body) {
        try {
            JsonObject request = parseBody(body);
            AuthRequest auth = decryptAndVerify(request);
            if (auth == null) {
                return error("鉴权失败，请刷新后重试");
            }
            
            ResetManager.ResetSession session = resetManager.getSession(auth.username);
            if (session == null || !session.verified) {
                return error("身份尚未验证或验证已过期");
            }
            
            if (!isValidPasswordHash(auth.passwordHash)) {
                return error("新密码格式不正确");
            }
            
            boolean updated = userStore.updatePassword(auth.username, auth.passwordHash);
            if (updated) {
                resetManager.removeSession(auth.username);
                JsonObject res = new JsonObject();
                res.addProperty("ok", true);
                res.addProperty("message", "密码重置成功，请使用新密码登录");
                return res;
            } else {
                return error("密码更新失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error("重置失败: " + safeErrorMessage(e));
        }
    }

    public boolean verifyInGameReset(String accountName, String playerName, String code) {
        return resetManager.verifyCode(accountName, playerName, code);
    }

    public interface PlayerVerifier {
        boolean isBound(String accountName, String playerName);
    }

    public interface TokenDeleter {
        void deleteToken(String playerName);
    }

    private JsonObject parseBody(String body) {
        return JsonParser.parseString(body == null ? "{}" : body).getAsJsonObject();
    }

    private AuthRequest decryptAndVerify(JsonObject request) {
        String handshakeId = getString(request, "handshakeId");
        String payloadBase64 = getString(request, "payload");
        String payloadEncBase64 = getString(request, "payloadEnc");
        String keyEncBase64 = getString(request, "keyEnc");
        String ivBase64 = getString(request, "iv");
        String hmacBase64 = getString(request, "hmac");
        if (handshakeId.isEmpty() || hmacBase64.isEmpty()) {
            return null;
        }
        HandshakeManager.Handshake handshake = handshakeManager.getValidHandshake(handshakeId);
        if (handshake == null) {
            return null;
        }
        String hmacSource = payloadBase64;
        if (!payloadEncBase64.isEmpty() && !ivBase64.isEmpty()) {
            hmacSource = payloadEncBase64 + "." + ivBase64;
        }
        byte[] expectedHmac = CryptoUtil.hmacSha256(handshake.hmacKey, hmacSource.getBytes(StandardCharsets.UTF_8));
        byte[] providedHmac = Base64.getDecoder().decode(hmacBase64);
        if (!MessageDigestUtil.isEqual(expectedHmac, providedHmac)) {
            return null;
        }
        String json;
        if (!payloadEncBase64.isEmpty() && !keyEncBase64.isEmpty() && !ivBase64.isEmpty()) {
            byte[] encryptedKey = Base64.getDecoder().decode(keyEncBase64);
            byte[] aesKey = rsaDecryptWithFallback(encryptedKey);
            json = new String(CryptoUtil.aesGcmDecrypt(
                    Base64.getDecoder().decode(payloadEncBase64),
                    Base64.getDecoder().decode(ivBase64),
                    aesKey
            ), StandardCharsets.UTF_8);
        } else {
            byte[] encrypted = Base64.getDecoder().decode(payloadBase64);
            json = new String(rsaDecryptWithFallback(encrypted), StandardCharsets.UTF_8);
        }
        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        String nonce = getString(payload, "nonce");
        if (!handshake.nonce.equals(nonce)) {
            return null;
        }
        String passwordHash = getString(payload, "passwordHash");
        String saltedHash = getString(payload, "saltedHash");
        if (!CryptoUtil.sha256Hex(passwordHash + nonce).equalsIgnoreCase(saltedHash)) {
            return null;
        }
        handshakeManager.consume(handshakeId);
        AuthRequest auth = new AuthRequest();
        auth.username = getString(payload, "username");
        auth.passwordHash = passwordHash;
        auth.captchaId = getString(payload, "captchaId");
        auth.captchaText = getString(payload, "captchaText");
        auth.playerName = getString(payload, "playerName");
        return auth;
    }

    private boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        String trimmed = username.trim();
        if (trimmed.length() < USERNAME_MIN || trimmed.length() > USERNAME_MAX) {
            return false;
        }
        return trimmed.matches("[A-Za-z0-9_]+");
    }

    private boolean isValidPasswordHash(String passwordHash) {
        if (passwordHash == null) {
            return false;
        }
        return passwordHash.matches("[A-Fa-f0-9]{64}");
    }

    private JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("message", message);
        return json;
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private byte[] rsaDecryptWithFallback(byte[] encrypted) {
        try {
            return CryptoUtil.rsaDecryptOaep(encrypted, keyPair.getPrivate(), rsaOaepHash);
        } catch (Exception first) {
            return CryptoUtil.rsaDecryptOaep(encrypted, keyPair.getPrivate(), rsaFallbackHash);
        }
    }

    private String safeErrorMessage(Exception e) {
        String name = e.getClass().getSimpleName();
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return name;
        }
        return name + " - " + message;
    }

    private static class AuthRequest {
        String username;
        String passwordHash;
        String captchaId;
        String captchaText;
        String playerName;
    }

    private static class MessageDigestUtil {
        static boolean isEqual(byte[] a, byte[] b) {
            if (a == null || b == null) {
                return false;
            }
            if (a.length != b.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < a.length; i++) {
                result |= a[i] ^ b[i];
            }
            return result == 0;
        }
    }
}
