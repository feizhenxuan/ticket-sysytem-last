package com.alipay.ticketbacked.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bcrypt 密码工具 — 对应 Python core/security.py hash_password / verify_password
 */
@Component
public class BcryptUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    /** 哈希密码 (cost=12) */
    public String hashPassword(String plain) {
        return ENCODER.encode(plain);
    }

    /** 校验密码 */
    public boolean verifyPassword(String plain, String hashed) {
        try {
            return ENCODER.matches(plain, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}