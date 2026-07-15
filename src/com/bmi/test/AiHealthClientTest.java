package com.bmi.test;

import com.bmi.client.AiHealthClient;
import com.bmi.exception.AiConfigException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AiHealthClientTest {

    @Test
    void testConstructorValid() throws AiConfigException {
        AiHealthClient client = new AiHealthClient("sk-test", "https://api.deepseek.com/v1");
        assertNotNull(client);
    }

    @Test
    void testConstructorNullKey() {
        assertThrows(AiConfigException.class, () -> {
            new AiHealthClient(null, "https://api.deepseek.com/v1");
        });
    }

    @Test
    void testConstructorEmptyUrl() {
        assertThrows(AiConfigException.class, () -> {
            new AiHealthClient("sk-test", "");
        });
    }
}