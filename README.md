# prueba-atlantida-api

API REST de Banco Atlántida para consultar cuentas y registrar transferencias entre cuentas del mismo banco.

**Stack:** Java 21 · Spring Boot 4.1.2 · Spring Data JPA · PostgreSQL 16 · Flyway · JUnit 5 · Mockito.

## Demo en línea (Render)

| Pieza    | URL                                             |
| -------- | ----------------------------------------------- |
| API      | https://prueba-atlantida-api-1.onrender.com/api |
| Frontend | https://prueba-atlantida-web.onrender.com       |

1. Abrir primero https://prueba-atlantida-api-1.onrender.com/api/accounts y esperar la respuesta: el servicio gratuito se suspende tras 15 minutos sin uso.
2. Luego abrir el frontend.

## Requisitos

- JDK 21
- Maven 3.9.x
- PostgreSQL 16 en `localhost:5432`

## Base de datos

Ejecutar como `postgres`:

```bash
psql -U postgres -h localhost -f database/crear-base-de-datos.sql
```

Crea el usuario `banco`/`banco` y las bases `prueba_atlantida` y `prueba_atlantida_test`.
Las tablas y los datos iniciales los crea Flyway al arrancar con el script del Anexo A
(`src/main/resources/db/migration/V1__esquema_inicial.sql`, copia en `database/script-anexo-a.sql`).
`V2` agrega columnas para idempotencia e historial y `V3` la tabla de auditoría `audit_log`.

## Levantar el backend

```bash
mvn spring-boot:run
```

API en `http://localhost:8080`.

| Variable                  | Valor por defecto                                   |
| ------------------------- | --------------------------------------------------- |
| `DB_URL`                  | `jdbc:postgresql://localhost:5432/prueba_atlantida` |
| `DB_USER` / `DB_PASSWORD` | `banco` / `banco`                                   |
| `CORS_ORIGINS`            | `http://localhost:5173,http://127.0.0.1:5173`       |

## Levantar el frontend

Ver el README de `prueba-atlantida-web` (`npm install` y `npm run dev`).

## Pruebas

```bash
mvn test
```

Usan la base `prueba_atlantida_test`. 24 pruebas: unitarias (Mockito) y de integración con PostgreSQL y MockMvc
(transferencias, validaciones, idempotencia, concurrencia, historial y auditoría).

## Endpoints

| Método | Ruta                                    | Respuesta                                                 |
| ------ | --------------------------------------- | --------------------------------------------------------- |
| GET    | `/api/accounts`                         | 200                                                       |
| GET    | `/api/accounts/{id}`                    | 200 · 404                                                 |
| POST   | `/api/transfers`                        | 201 · 200 (reintento idempotente) · 400 · 404 · 409 · 422 |
| GET    | `/api/transfers/{id}`                   | 200 · 404                                                 |
| GET    | `/api/accounts/{id}/transfers`          | 200 · 404                                                 |
| GET    | `/api/audit?accountId=&outcome=&limit=` | 200 · 400                                                 |

Cabeceras opcionales de `POST /api/transfers`: `Idempotency-Key` (evita procesar dos veces la misma solicitud) y
`X-Usuario` (usuario que se registra en la auditoría; si no se envía, `anonimo`).

```bash
curl http://localhost:8080/api/accounts
```

```json
[{ "id": 1001, "holderName": "Juan Pérez", "balance": 5000.00 }, ...]
```

```bash
curl http://localhost:8080/api/accounts/1001
```

```json
{ "id": 1001, "holderName": "Juan Pérez", "balance": 5000.0 }
```

```bash
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 7d3c9a52-0b7e-4f4e-9a52-1c2d3e4f5a6b" \
  -d '{"sourceAccountId":1001,"destinationAccountId":1002,"amount":500.00}'
```

`201 Created`

```json
{
  "id": 1,
  "amount": 500.0,
  "createdAt": "2026-11-17T09:44:12",
  "source": {
    "accountId": 1001,
    "holderName": "Juan Pérez",
    "previousBalance": 5000.0,
    "newBalance": 4500.0
  },
  "destination": {
    "accountId": 1002,
    "holderName": "María López",
    "previousBalance": 2000.0,
    "newBalance": 2500.0
  }
}
```

`422 Unprocessable Entity`

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Fondos insuficientes en la cuenta 1001. Los saldos no cambiaron.",
  "path": "/api/transfers",
  "errors": []
}
```

```bash
curl http://localhost:8080/api/accounts/1001/transfers
```

```json
[
  {
    "transferId": 1,
    "direction": "ENVIADA",
    "counterpartAccountId": 1002,
    "counterpartHolderName": "María López",
    "amount": 500.0,
    "balanceAfter": 4500.0,
    "createdAt": "2026-11-17T09:44:12"
  }
]
```

```bash
curl "http://localhost:8080/api/audit?accountId=1001&outcome=RECHAZADA"
```

```json
[
  {
    "id": 4,
    "createdAt": "2026-11-17T10:40:12",
    "action": "TRANSFERENCIA",
    "outcome": "RECHAZADA",
    "httpStatus": 422,
    "message": "Fondos insuficientes en la cuenta 1001. Los saldos no cambiaron.",
    "username": "cajero01",
    "clientIp": "127.0.0.1",
    "userAgent": "Mozilla/5.0",
    "sourceAccountId": 1001,
    "destinationAccountId": 1003,
    "amount": 6000.0,
    "idempotencyKey": null,
    "transferId": null
  }
]
```

Colección de Postman: `postman/Prueba-Atlantida-API.postman_collection.json` (17 peticiones).

## Valores agregados

- **Historial por cuenta:** `GET /api/accounts/{id}/transfers` y pantalla de historial en el frontend.
- **Idempotencia:** cabecera `Idempotency-Key` con restricción `UNIQUE` en la base; un reintento devuelve la transferencia original sin mover dinero.
- **Control de concurrencia:** las dos cuentas se bloquean con `SELECT ... FOR UPDATE` en orden de id; probado con 10 transferencias simultáneas.
- **Auditoría:** cada intento de transferencia queda en `audit_log` como `EXITOSA`, `REINTENTO` o `RECHAZADA`, con código HTTP, mensaje, usuario, IP, navegador, cuentas, monto, `Idempotency-Key` y número de transferencia. Consulta en `GET /api/audit`.
- **Docker Compose:** con `prueba-atlantida-web` en la misma carpeta, `docker compose up --build` levanta PostgreSQL, backend (`:8080`) y frontend (`:5173`).

## Decisiones técnicas

- Capas `controller` → `service` → `repository`, con DTOs (`record`) en la API.
- Cada transferencia es una sola transacción (`@Transactional`): débito, crédito y registro se confirman juntos o no se guarda nada.
- Esquema versionado con Flyway (`ddl-auto=validate`); el Anexo A se usa sin cambios y la V2 solo agrega columnas e índices.
- Montos en `BigDecimal` / `NUMERIC(18,2)`.
- Errores con formato único mediante `@RestControllerAdvice`: 400 validación, 404 no existe, 409 conflicto, 422 regla de negocio.
- Auditoría: las transferencias exitosas y los reintentos se registran en la misma transacción que la transferencia (no existe una sin la otra); los rechazos se registran desde el manejador global de errores en una transacción independiente (`REQUIRES_NEW`), por lo que quedan guardados aunque la transferencia haga rollback.
- Pruebas de integración contra PostgreSQL real para validar bloqueos y restricciones.
