package com.voiceish.sim;

final class BridgeException extends Exception {
    final String code;
    BridgeException(String code, String message) { super(message); this.code = code; }
}
