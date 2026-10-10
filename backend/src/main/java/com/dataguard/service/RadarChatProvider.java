package com.dataguard.service;

import java.util.List;

public interface RadarChatProvider {
    record Turn(String role, String content) {}
    String complete(List<Turn> turns);
}
