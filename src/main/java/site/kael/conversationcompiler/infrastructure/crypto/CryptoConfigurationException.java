package site.kael.conversationcompiler.infrastructure.crypto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Indicates that encrypted provider credentials cannot be persisted safely. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CryptoConfigurationException extends IllegalStateException {
    public CryptoConfigurationException(String message) { super(message); }
}
