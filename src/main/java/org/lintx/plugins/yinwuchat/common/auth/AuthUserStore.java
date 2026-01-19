package org.lintx.plugins.yinwuchat.common.auth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AuthUserStore {
    private static final String FILE_NAME = "web-users.json";
    private final File file;
    private final Gson gson = new Gson();
    private final Map<String, UserRecord> users = new HashMap<>();

    public AuthUserStore(File dataFolder) {
        this.file = new File(dataFolder, FILE_NAME);
        load();
    }

    public synchronized boolean exists(String username) {
        return users.containsKey(normalize(username));
    }

    public synchronized boolean register(String username, String passwordHash) {
        String key = normalize(username);
        if (users.containsKey(key)) {
            return false;
        }
        String salt = CryptoUtil.randomHex(16);
        String saltedHash = CryptoUtil.sha256Hex(passwordHash + salt);
        UserRecord record = new UserRecord();
        record.username = username.trim();
        record.salt = salt;
        record.saltedHash = saltedHash;
        record.createdAt = System.currentTimeMillis();
        users.put(key, record);
        save();
        return true;
    }

    public synchronized boolean verify(String username, String passwordHash) {
        UserRecord record = users.get(normalize(username));
        if (record == null) {
            return false;
        }
        String saltedHash = CryptoUtil.sha256Hex(passwordHash + record.salt);
        return saltedHash.equalsIgnoreCase(record.saltedHash);
    }

    public synchronized String getDisplayName(String username) {
        UserRecord record = users.get(normalize(username));
        return record == null ? "" : record.username;
    }

    public synchronized boolean delete(String username) {
        String key = normalize(username);
        if (users.containsKey(key)) {
            users.remove(key);
            save();
            return true;
        }
        return false;
    }

    public synchronized boolean updatePassword(String username, String newPasswordHash) {
        UserRecord record = users.get(normalize(username));
        if (record == null) {
            return false;
        }
        String salt = CryptoUtil.randomHex(16);
        String saltedHash = CryptoUtil.sha256Hex(newPasswordHash + salt);
        record.salt = salt;
        record.saltedHash = saltedHash;
        save();
        return true;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, UserRecord>>() {}.getType();
            Map<String, UserRecord> data = gson.fromJson(reader, type);
            if (data != null) {
                users.clear();
                users.putAll(data);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, false)) {
                gson.toJson(users, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    public static class UserRecord {
        public String username = "";
        public String salt = "";
        public String saltedHash = "";
        public long createdAt = 0L;
    }
}
