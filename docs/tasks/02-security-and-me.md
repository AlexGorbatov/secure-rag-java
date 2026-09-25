# 02. Keycloak, JWT и `GET /api/v1/me` ✅

Задача уже сделана. Если хочешь закрепить — удали файлы из списка ниже и напиши их заново по этому
описанию, тесты подскажут, всё ли правильно.

## Зачем

Всё в проекте держится на одном вопросе: **кто спрашивает и что ему можно**. Ответ берётся только
из подписанного JWT. Здесь токен превращается в объект `Entitlements`, который дальше будет
фильтровать документы.

## Путь запроса

```
GET /api/v1/me   Authorization: Bearer eyJ...
 └─ BearerTokenAuthenticationFilter              (Spring Security, до контроллера)
     └─ JwtDecoder
         ├─ скачивает ключи Keycloak: {issuer}/protocol/openid-connect/certs  (кэш)
         └─ проверяет подпись, iss == issuer-uri, aud содержит securerag-api, exp
     └─ SecurityContext ← JwtAuthenticationToken(principal = Jwt)
 └─ web/CurrentUserController.me(@AuthenticationPrincipal Jwt jwt)
     └─ service/EntitlementsResolver.resolve(jwt) → model/Entitlements
 ← web/dto/CurrentUserResponse
```

## Файлы

| Файл | Что внутри |
|---|---|
| `keycloak/securerag-realm.json` | realm `securerag`, пользователи alice/bob, группы, клиент `securerag-web` с мапперами `aud` и `groups` |
| `compose.yaml` → сервис `keycloak` | порт 8180 (фиксированный — он попадает в `iss` токена), импорт realm, healthcheck |
| `config/SecurityConfig` | `SecurityFilterChain`: публичные только GET `/actuator/health`, `/v3/api-docs/**`, Swagger; остальное — JWT; `STATELESS`; CSRF выключен |
| `config/ClaimsProperties` | `@ConfigurationProperties("securerag.security.claims")`: где в токене `username`, `groups`, `roles` |
| `config/OpenApiConfig` | бин `OpenAPI`: одна схема авторизации `login` — OAuth2 authorization code + PKCE; адреса из `securerag.openapi.*` |
| `model/Entitlements` | `record(subject, username, Set groups, Set roles)` |
| `service/EntitlementsResolver` | `Entitlements resolve(Jwt)`: достаёт claims по пути с точками (`realm_access.roles`) |
| `web/CurrentUserController` | `GET /api/v1/me` |
| `web/dto/CurrentUserResponse` | ответ |

## Ключевые вызовы

```java
http.authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, PUBLIC).permitAll().anyRequest().authenticated())
    .oauth2ResourceServer(o -> o.jwt(j -> {}))
jwt.getSubject(); jwt.getClaims(); // Map<String,Object>
```

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/securerag
spring.security.oauth2.resourceserver.jwt.audiences=securerag-api
```

## Подводные камни

- `iss` в токене должен **побайтово** совпадать с `issuer-uri`. `localhost` ≠ `127.0.0.1`,
  `http://keycloak:8080` ≠ `http://localhost:8180`.
- Если в realm-файле задать свой массив `clientScopes`, Keycloak не создаст встроенные scopes, и из
  токена пропадут `sub` и роли. Мапперы лучше вешать прямо на клиента.
- Отсутствующий или кривой claim должен давать **пустые** права, а не «группу по умолчанию».

## Эксперименты

1. Получи токен alice (команда в `README.md` задач), вставь его на https://jwt.io и найди `iss`,
   `aud`, `groups`, `realm_access.roles`.
2. Поменяй в `application.properties` `audiences` на `other-api` и вызови `/api/v1/me` — почему 401?
3. Посмотри тесты `CurrentUserApiTest`: как `jwt().jwt(...)` подменяет токен без Keycloak?

## Готово, когда

Можешь нарисовать путь запроса выше по памяти и объяснить, где вернётся 401.
