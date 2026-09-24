package com.altronixsoft.securerag.web;

import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.EntitlementsResolver;
import com.altronixsoft.securerag.web.dto.CurrentUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Current user", description = "Identity and entitlements resolved from the access token")
class CurrentUserController {

    private final EntitlementsResolver entitlementsResolver;

    CurrentUserController(EntitlementsResolver entitlementsResolver) {
        this.entitlementsResolver = entitlementsResolver;
    }

    @GetMapping
    @Operation(
            description = "Returns the caller's identity and the groups and roles that decide which documents they can see.")
    @ApiResponse(responseCode = "200", description = "Entitlements of the caller")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        Entitlements entitlements = entitlementsResolver.resolve(jwt);
        return new CurrentUserResponse(
                entitlements.subject(), entitlements.username(), entitlements.groups(), entitlements.roles());
    }

}
