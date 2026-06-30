# yorbank-backend



\[!\[Java Version](https://img.shields.io/badge/Java-21-orange?logo=openjdk\&logoColor=white)](https://openjdk.org/)

\[!\[Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)

\[!\[GitHub Actions CI](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blue?logo=githubactions\&logoColor=white)](#cicd-github-actions)

\[!\[Database](https://img.shields.io/badge/Database-PostgreSQL%2016-blue?logo=postgresql\&logoColor=white)](https://www.postgresql.org/)

\[!\[Cache \& Session](https://img.shields.io/badge/Cache-Redis-red?logo=redis\&logoColor=white)](https://redis.io/)

\[!\[Event Driven](https://img.shields.io/badge/Event--Driven-Apache%20Kafka-black?logo=apachekafka\&logoColor=white)](https://kafka.apache.org/)



Un backend de core bancario robusto y moderno para portafolios profesionales, inspirado en las necesidades y flujos de negocio reales de la banca móvil (App Móvil Flutter / Web). 



Este sistema proporciona una arquitectura limpia basada en dominios y servicios para gestionar autenticación segura con JWT, simulación y desembolso de préstamos con generación matemática de archivos PDF sin dependencias externas, transferencias bancarias internas e interbancarias impulsadas por eventos, administración de tarjetas y pagos de servicios públicos y digitales.



\---



\##  Arquitectura del Sistema



El backend está diseñado siguiendo una arquitectura de capas orientada a dominios autónomos. Utiliza un esquema transaccional directo con `JdbcTemplate` para optimizar el rendimiento en consultas críticas y flujos financieros complejos, mientras que aprovecha `Spring Data JPA` en los flujos principales de entidades base.



```mermaid

graph TD

&#x20;   Client\[Cliente / App Flutter / Postman] -->|Peticiones REST| Security\[Spring Security / JWT Filter]

&#x20;   

&#x20;   subgraph Backend \[YBank Core Banking API]

&#x20;       Security --> Auth\[AuthController]

&#x20;       Security --> Account\[AccountController]

&#x20;       Security --> Card\[CardController]

&#x20;       Security --> Transfer\[TransferController]

&#x20;       Security --> Payment\[PaymentController]

&#x20;       Security --> Loan\[LoanController]

&#x20;       

&#x20;       Auth --> AuthService\[AuthService]

&#x20;       AuthService --> JWT\[JwtService]

&#x20;       AuthService --> DB\_Users\[(PostgreSQL)]

&#x20;       AuthService --> Redis\[(Redis - OTP Demo Cache)]

&#x20;       

&#x20;       Account --> DB\_Acc\[(PostgreSQL)]

&#x20;       Card --> JDBC\[JdbcTemplate - Direct SQL]

&#x20;       Transfer --> JDBC

&#x20;       Payment --> JDBC

&#x20;       Loan --> JDBC

&#x20;       

&#x20;       Transfer -->|Produce evento| Kafka\[Apache Kafka - Topic: banking.transfer.completed]

&#x20;       Loan -->|Generación matemática pura| PDF\[SimplePdf Generator]

&#x20;   end



&#x20;   subgraph Infraestructura \[Infraestructura Local Docker]

&#x20;       Redis

&#x20;       DB\_Users

&#x20;       DB\_Acc

&#x20;       JDBC --> PostgreSQL\[(PostgreSQL 16)]

&#x20;       Flyway\[Flyway Migrations] -->|Control de versiones y Semillas| PostgreSQL

&#x20;   end



&#x20;   style Client fill:#eceff1,stroke:#37474f,stroke-width:2px;

&#x20;   style Security fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;

&#x20;   style Backend fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;

&#x20;   style Infraestructura fill:#efebe9,stroke:#4e342e,stroke-width:2px;

```



\---



\##  Stack Tecnológico y Racional Técnico



\*   \*\*Java 21\*\*: Uso de características modernas de lenguaje como \*Records\* para DTOs limpios, bloques de texto para consultas SQL y mejoras de rendimiento.

\*   \*\*Spring Boot 3.3.5\*\*: Base para el desarrollo rápido y estructurado del microservicio.

\*   \*\*Spring Security \& JWT\*\*: Pipeline de seguridad robusto que valida firmas tokens JWT de forma stateless y restringe endpoints según el rol (`CUSTOMER`, `ADMIN`).

\*   \*\*Spring Data JPA / Hibernate\*\*: Utilizado para la gestión del ciclo de vida de entidades persistentes complejas como usuarios y cuentas bancarias.

\*   \*\*JdbcTemplate (Direct SQL)\*\*: Para optimizar el rendimiento y controlar explícitamente los bloqueos de base de datos (`SELECT ... FOR UPDATE`) en transferencias, pagos y desembolsos de préstamos para evitar condiciones de carrera (\*Race Conditions\*).

\*   \*\*PostgreSQL 16\*\*: Motor relacional de grado empresarial para garantizar consistencia ACID en la base de datos transaccional.

\*   \*\*Flyway\*\*: Gestión y versionamiento de la base de datos mediante migraciones de bases de datos automáticas al iniciar la aplicación.

\*   \*\*Redis\*\*: Utilizado para almacenamiento rápido en memoria y validaciones de tokens / OTPs temporales.

\*   \*\*Apache Kafka\*\*: Soporte para arquitectura orientada a eventos para notificar transferencias completadas de manera asíncrona mediante el topic `banking.transfer.completed`.

\*   \*\*OpenAPI / Swagger\*\*: Auto-documentación interactiva del API.

\*   \*\*Docker \& Docker Compose\*\*: Contenerización de servicios externos (BD, Redis, Kafka) para una ejecución local rápida y reproducible.



\---



\##  Estructura del Código



El backend está organizado por dominios de negocio funcionales dentro de `com.ybank.core`:



\*   \[`auth`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/auth): Registro de clientes, inicio de sesión seguro en 2 pasos (OTP demo), servicio de generación y validación de tokens JWT.

\*   \[`account`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/account): Consultas de saldos consolidados, detalle de cuentas de ahorro o corriente, historial de movimientos financieros.

\*   \[`card`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/card): Administración de tarjetas asociadas al cliente (activación o bloqueo temporal de seguridad).

\*   \[`transfer`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/transfer): Transferencias entre cuentas internas de YBank y hacia bancos externos (con cálculo dinámico de comisiones por banco). Integra la publicación de eventos en Kafka.

\*   \[`payment`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/payment): Pago de servicios básicos (luz, agua, internet, educación), recargas móviles y pagos virtuales simplificados mediante funcionalidad tipo "Yape".

\*   \[`loan`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/loan): Motor de préstamos digitales. Ejecuta simulaciones de crédito con amortización francesa, evalúa la capacidad de pago del cliente, desembolsa fondos directamente en cuenta de ahorros, y genera dinámicamente un cronograma detallado de cuotas en formato PDF codificado en bytes puros sin librerías externas.

\*   \[`common`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/common): Estructura estandarizada de respuestas del API (`ApiResponse`) y manejador global de excepciones del negocio.

\*   \[`config`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/java/com/ybank/core/config): Configuración del middleware de seguridad y definición de la especificación OpenAPI de Swagger.



\---



\##  Diseño de la Base de Datos



Las migraciones de bases de datos son administradas por Flyway ubicadas en \[`db/migration`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/main/resources/db/migration). El esquema consta de 18 tablas detalladas a continuación:



| Tabla | Propósito | Relaciones Principales |

| :--- | :--- | :--- |

| `users` | Entidad principal de credenciales, datos de acceso y roles (`CUSTOMER`, `ADMIN`). | - |

| `customer\_profiles` | Información de contacto del cliente (teléfono, dirección, segmento de cliente como "ORO"). | `customer\_id` ➔ `users(id)` |

| `accounts` | Cuentas bancarias activas del cliente, con divisa (PEN/USD) y saldo en tiempo real. | `customer\_id` ➔ `users(id)` |

| `account\_movements` | Historial de ingresos y egresos de las cuentas. Categorizado por tipo (Sueldo, Yape, QR, etc.). | `account\_id` ➔ `accounts(id)` |

| `user\_cards` | Tarjetas de débito o crédito asociadas con número enmascarado y estado de bloqueo. | `customer\_id` ➔ `users(id)` |

| `transfers` | Registro histórico de transferencias internas y a otros bancos. | `customer\_id` ➔ `users(id)` |

| `external\_banks` | Catálogo de bancos nacionales externos con sus respectivas tarifas de transferencia. | - |

| `beneficiaries` | Agenda de contactos bancarios frecuentes guardados por el cliente. | `customer\_id` ➔ `users(id)` |

| `yape\_contacts` | Contactos telefónicos habilitados para pagos inmediatos en red móvil. | `customer\_id` ➔ `users(id)` |

| `yape\_payments` | Transferencias inmediatas asociadas a números telefónicos directos. | `customer\_id` ➔ `users(id)` |

| `mobile\_operators` | Operadores móviles válidos para recargas de saldo (Claro, Movistar, Entel, Bitel). | - |

| `mobile\_recharges` | Transacciones de recarga de saldo celular desde cuentas bancarias. | `customer\_id` ➔ `users(id)` |

| `service\_bills` | Directorio de empresas prestadoras de servicios básicos afiliadas (Luz, Agua, etc.). | - |

| `bill\_payments` | Registro de boletas pagadas y transacciones de servicios públicos. | `customer\_id` ➔ `users(id)` |

| `loan\_products` | Tipos de préstamos ofrecidos (Personal Oro, Emprendedor, etc.) con sus tasas y plazos. | - |

| `loan\_applications` | Préstamos solicitados y desembolsados con resumen de costos y TCEA. | `customer\_id` ➔ `users(id)` |

| `loan\_installments` | Cuotas del cronograma del préstamo con desglose de capital, interés, seguro y comisión. | `loan\_application\_id` ➔ `loan\_applications(id)` |

| `notifications` | Bandeja de entrada de notificaciones seguras de la app móvil para el cliente. | `customer\_id` ➔ `users(id)` |



\---



\##  Guía de Ejecución Local



\### Prerrequisitos

\*   \*\*JDK 21\*\* instalado localmente.

\*   \*\*Maven 3.9+\*\* (opcional, se puede usar `./mvnw`).

\*   \*\*Docker Desktop\*\* (con soporte para Compose).



\### Paso 1: Levantar Infraestructura Externa

Inicia la base de datos PostgreSQL, Redis y Kafka utilizando Docker Compose:

```bash

docker compose up -d

```



> \[!NOTE]

> Las credenciales de base de datos por defecto se auto-configuran con el archivo `docker-compose.yml` (`ybank` / `ybank123`).



\### Paso 2: Configurar Variables de Entorno (Opcional)

Puedes guiarte del archivo \[`.env.example`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/.env.example) para crear un archivo `.env` en la raíz con las siguientes variables si necesitas cambiar los puertos de la infraestructura local:

\*   `DB\_URL`: JDBC URL de PostgreSQL.

\*   `DB\_USER` / `DB\_PASSWORD`: Credenciales del motor PostgreSQL.

\*   `REDIS\_HOST` / `REDIS\_PORT`: Conexión al servidor caché.

\*   `KAFKA\_BOOTSTRAP\_SERVERS`: Direcciones para el cliente de mensajería Kafka.

\*   `JWT\_SECRET`: Llave simétrica de al menos 256 bits para firmar los tokens JWT.



\### Paso 3: Compilar y Ejecutar la Aplicación

Ejecuta los siguientes comandos para compilar la aplicación, ejecutar la suite de pruebas unitarias y levantar el servidor web:

```bash

mvn clean install

mvn spring-boot:run

```



El backend iniciará en el puerto \*\*`8080`\*\* por defecto.



\*   \*\*API Documentation (Swagger UI)\*\*: \[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

\*   \*\*Actuator Healthcheck\*\*: \[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)



\---



\##  Catálogo de Endpoints Clave



Todos los endpoints (excepto los de autenticación pública) requieren la cabecera `Authorization: Bearer <JWT\_TOKEN>`.



\###  Autenticación y Registro (`/api/v1/auth`)

\*   `POST /register`: Registrar un nuevo cliente.

\*   `POST /login/prepare`: Iniciar autenticación enviando número de documento de identidad y contraseña. Valida las credenciales y devuelve datos para el paso OTP (2FA).

\*   `POST /login`: Autenticación directa por credenciales para desarrollo (sin OTP).

\*   `POST /otp/verify`: Envía el código OTP recibido para obtener el token final de acceso `accessToken`.

\*   `GET /me`: Obtener información del perfil del usuario autenticado.



\###  Cuentas y Tarjetas (`/api/v1/accounts`, `/api/v1/cards`)

\*   `GET /accounts`: Listar todas las cuentas de ahorros/corrientes con sus saldos respectivos.

\*   `GET /accounts/home-summary`: Resumen simplificado de saldo consolidated y tarjeta principal para la pantalla principal de la App.

\*   `GET /accounts/movements`: Obtener los últimos 20 movimientos transaccionales del cliente.

\*   `GET /cards`: Mostrar tarjetas de débito/crédito vinculadas.

\*   `PATCH /cards/{id}/status`: Activar o bloquear temporalmente una tarjeta (`ACTIVE` o `LOCKED`).



\###  Transferencias (`/api/v1/transfers`)

\*   `GET /transfers/banks`: Listar bancos del sistema y sus tarifas de transferencia interbancaria.

\*   `POST /transfers`: Procesar transferencia. Si es interna (`YBANK`), se procesa inmediatamente y se actualizan los saldos de forma atómica en base de datos. Si es externa, se calcula comisión y se envía en estado pendiente publicando un mensaje a Apache Kafka.

\*   `GET /transfers`: Obtener historial de transferencias enviadas.



\###  Pagos y Recargas (`/api/v1/payments`)

\*   `GET /payments/services`: Lista servicios disponibles para pagar (luz, agua, teléfono, etc.).

\*   `POST /payments`: Ejecutar el pago de un recibo de servicios públicos.

\*   `POST /payments/recharge`: Realizar recargas de saldo móvil a operadores nacionales.

\*   `POST /payments/yape`: Enviar dinero de forma inmediata utilizando únicamente el número telefónico del destinatario.



\###  Préstamos Financieros (`/api/v1/loans`)

\*   `GET /loans/products`: Listar productos de préstamos habilitados.

\*   `POST /loans/simulate`: Simular un préstamo bajo sistema de amortización francés. Calcula la TCEA real y analiza la capacidad crediticia del cliente (`SALUDABLE`, `AJUSTADO`, `RIESGO\_ALTO`) comparando la cuota mensual contra su nivel de ingresos declarados.

\*   `POST /loans/applications`: Solicitar préstamo formalmente. Desembolsa el capital directamente en la cuenta bancaria del cliente y programa el cronograma de cuotas futuras.

\*   `GET /loans/applications`: Historial de préstamos activos del cliente.

\*   `GET /loans/applications/{id}/schedule.pdf`: Genera y descarga el PDF formal con el cronograma detallado de cuotas de amortización.



\---



\## CI/CD: GitHub Actions



El proyecto cuenta con un flujo automatizado de integración continua desarrollado en GitHub Actions ubicado en \[`.github/workflows/ci.yml`](file:///.github/workflows/ci.yml). 



Este pipeline realiza los siguientes pasos en cada `push` a las ramas `main` o `develop`, y en cada `pull\_request` dirigido a `main`:

1\.  \*\*Checkout Code\*\*: Descarga el código fuente del repositorio en el agente virtual de ejecución (`ubuntu-latest`).

2\.  \*\*Set up Java JDK 21\*\*: Configura el entorno virtual con el SDK de Java 21 (versión Temurin) y activa el almacenamiento en caché nativo de Maven (`cache: 'maven'`) para acelerar la descarga de dependencias en compilaciones futuras.

3\.  \*\*Check Environment Versions\*\*: Imprime en consola las versiones específicas de Java y Maven utilizadas para auditoría técnica.

4\.  \*\*Build, Test and Package\*\*: Ejecuta el comando de Maven `mvn -B clean verify` que compila el código, ejecuta toda la suite de pruebas unitarias implementadas (incluyendo \[`JwtServiceTest`](file:///c:/Users/USER/Desktop/ybank-core-banking-api/src/test/java/com/ybank/core/auth/JwtServiceTest.java)) y genera el archivo empaquetado `.jar` final, garantizando que el código de la rama nunca sufra de regresiones ni errores de compilación antes del merge.



