# Gatling Load Tests - Balatro Backend

Proyecto de pruebas de carga usando Gatling (Scala) para el backend de Balatro.

## 🚀 Requisitos

- Java 17+
- Maven 3.6+
- Gatling 3.10.3

## 📋 Escenarios de Prueba

### 1. BasicLoadTest
Prueba básica de carga con usuarios concurrentes:
- 50 usuarios en rampa durante 30 segundos
- 10 usuarios/segundo durante 1 minuto
- Verifica endpoints de health y autenticación

### 2. StressTest
Prueba de estrés intensiva con 5 fases:
1. Calentamiento: 100 usuarios en 1 minuto
2. Carga constante: 50 usuarios/seg por 2 minutos
3. Pico: 300 usuarios en 30 segundos
4. Sostenimiento: 100 usuarios/seg por 1 minuto
5. Descenso: 50 usuarios en 1 minuto

### 3. LoadBalancerTest
Verifica la distribución de carga entre instancias EC2:
- 20 usuarios simultáneos iniciales
- Rampa de 50 usuarios en 1 minuto
- Captura información del servidor para verificar distribución

## 🏃 Cómo ejecutar

### Ejecutar una simulación específica:

```powershell
# Desde el directorio load-tests
cd load-tests

# Prueba básica
mvn gatling:test -Dgatling.simulationClass=simulations.BasicLoadTest

# Prueba de estrés
mvn gatling:test -Dgatling.simulationClass=simulations.StressTest

# Prueba de balanceo de carga
mvn gatling:test -Dgatling.simulationClass=simulations.LoadBalancerTest
```

### Ejecutar todas las simulaciones:

```powershell
mvn gatling:test
```

## 📊 Reportes

Después de ejecutar las pruebas, Gatling genera reportes HTML en:
```
load-tests/target/gatling/results/
```

Abre el archivo `index.html` en tu navegador para ver:
- Gráficos de tiempo de respuesta
- Throughput (peticiones/segundo)
- Distribución de códigos de respuesta
- Percentiles de latencia
- Estadísticas detalladas

## 🎯 Métricas Monitoreadas

- **Tiempo de respuesta medio**: < 1000ms
- **Tiempo de respuesta máximo**: < 2000ms
- **99th percentile**: < 5000ms (stress test)
- **Tasa de éxito**: > 95% (básica), > 90% (estrés)

## ⚙️ Configuración

Edita las simulaciones en `src/test/scala/simulations/` para:
- Cambiar la URL base del ALB
- Ajustar número de usuarios concurrentes
- Modificar duración de las pruebas
- Agregar nuevos escenarios

## 🔧 Personalización

### Cambiar la URL del backend:

Edita en cada archivo de simulación:
```scala
val httpProtocol = http
  .baseUrl("http://TU-ALB-DNS.amazonaws.com")
```

### Agregar headers de autenticación:

```scala
val httpProtocol = http
  .baseUrl("http://...")
  .header("Authorization", "Bearer ${token}")
```

### Crear escenarios con datos:

```scala
val feeder = csv("data/users.csv").circular

val scenario = scenario("User Login")
  .feed(feeder)
  .exec(
    http("Login")
      .post("/login")
      .body(StringBody("""{"username":"${username}","password":"${password}"}"""))
  )
```

## 📈 Interpretación de Resultados

### Respuesta rápida (< 500ms): ✅ Excelente
### Respuesta aceptable (500-1000ms): ⚠️ Bien
### Respuesta lenta (1000-2000ms): ⚠️ Revisar
### Respuesta muy lenta (> 2000ms): ❌ Problema

## 🐛 Troubleshooting

### Error: Connection refused
- Verifica que el ALB esté accesible
- Confirma que los targets estén "healthy"

### Timeout errors
- Aumenta los timeouts en la configuración HTTP
- Reduce el número de usuarios concurrentes

### Out of memory
- Aumenta heap de Java: `MAVEN_OPTS="-Xmx2g"`
- Reduce la duración de las pruebas

## 📚 Recursos

- [Documentación Gatling](https://gatling.io/docs/current/)
- [Gatling Cheat Sheet](https://gatling.io/docs/current/cheat-sheet/)
- [Scala Syntax](https://docs.scala-lang.org/tour/tour-of-scala.html)
