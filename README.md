# Rappi Favor

Plataforma universitaria de favores y mandados — ITESO 2026.

## Stack

- **Backend**: Java 15 + Spark Java + MongoDB Atlas + Firebase Admin SDK
- **Frontend**: HTML / CSS / JS vanilla
- **Auth**: Firebase Authentication (email/contraseña)
- **Chat**: Firebase Realtime Database

---

## Requisitos previos

- Java JDK 15+
- Maven 3.x
- VS Code con extensión **Live Server**

---

## Configuración inicial

### 1. Credenciales de Firebase
En [Firebase Console](https://console.firebase.google.com) → proyecto **rappifavor** → ⚙️ → Cuentas de servicio → **Generar nueva clave privada**.

Guarda el archivo como:
```
src/main/resources/firebase-credentials.json
```

### 2. Variables de entorno
Crea el archivo `.env` en la raíz del proyecto:

```
MONGO_URI=mongodb+srv://admin:<password>@cluster0.9yubdvi.mongodb.net/?appName=Cluster0
MONGO_DB_NAME=rappifavor
FIREBASE_CREDENTIALS_PATH=src/main/resources/firebase-credentials.json
```

> Pide el `MONGO_URI` completo al líder del equipo.

---

## Cómo correr el proyecto

### Backend
```powershell
mvn package -DskipTests
.\run.ps1
```
El servidor inicia en `http://localhost:8080`.

### Frontend
Clic derecho en `frontend/index.html` → **Open with Live Server**.

Se abre en `http://127.0.0.1:5500/frontend/index.html`.

---

## Tests

```powershell
mvn test -Dtest="UserServiceTest,OrderServiceTest,DisputeServiceTest"
```

---

## Roles

| Rol | Puede |
|-----|-------|
| USUARIO | Crear pedidos, cancelar, abrir disputas |
| REPARTIDOR | Aceptar, iniciar y confirmar entregas |
| ADMINISTRADOR | Gestionar usuarios y resolver disputas |

---

## Emails válidos

Solo se permiten correos `@iteso.mx`.
