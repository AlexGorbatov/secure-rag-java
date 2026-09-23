package com.altronixsoft.securerag.web.dto;

import java.util.Set;

public record CurrentUserResponse(String subject, String username, Set<String> groups, Set<String> roles) {
}
