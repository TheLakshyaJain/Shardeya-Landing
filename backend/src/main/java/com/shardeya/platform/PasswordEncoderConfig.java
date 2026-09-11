package com.shardeya.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Argon2id per CLAUDE.md rule #? / 00-ARCHITECTURE.md §5: m=64MB, t=3, p=4. */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // (saltLength, hashLength, parallelism, memoryKB, iterations)
        return new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    }
}
