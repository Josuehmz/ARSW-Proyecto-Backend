package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Prueba básica de carga para el backend de Balatro
 * Simula usuarios concurrentes accediendo al endpoint de health
 */
class BasicLoadTest extends Simulation {

  // Configuración del protocolo HTTP
  val httpProtocol = http
    .baseUrl("http://balatro-alb-1252182129.us-east-1.elb.amazonaws.com")
    .acceptHeader("application/json")
    .userAgentHeader("Gatling Load Test")

  // Escenario 1: Verificación de health endpoint
  val healthCheckScenario = scenario("Health Check Load Test")
    .exec(
      http("GET /actuator/health")
        .get("/actuator/health")
        .check(status.is(200))
        .check(jsonPath("$.status").is("UP"))
    )

  // Escenario 2: Prueba de autenticación (esperando 401)
  val authCheckScenario = scenario("Authentication Check")
    .exec(
      http("GET / (without auth)")
        .get("/")
        .check(status.is(401))
    )

  // Configuración de carga: Rampa de usuarios
  setUp(
    healthCheckScenario.inject(
      rampUsers(50) during (30.seconds), // 50 usuarios en 30 segundos
      constantUsersPerSec(10) during (1.minute) // 10 usuarios/seg por 1 minuto
    ),
    authCheckScenario.inject(
      rampUsers(20) during (20.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(2000), // Tiempo de respuesta máximo < 2s
     global.successfulRequests.percent.gt(95) // 95% de peticiones exitosas
   )
}
