package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Prueba de estrés intensiva
 * Simula un pico de tráfico alto para probar los límites del sistema
 */
class StressTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://balatro-alb-1252182129.us-east-1.elb.amazonaws.com")
    .acceptHeader("application/json")

  val stressScenario = scenario("Stress Test Scenario")
    .exec(
      http("Health Check")
        .get("/actuator/health")
        .check(status.in(200, 503)) // Acepta 200 o 503 (sobrecarga)
    )
    .pause(100.milliseconds, 500.milliseconds) // Pausa aleatoria entre requests

  setUp(
    stressScenario.inject(
      // Fase 1: Calentamiento
      rampUsers(100) during (1.minute),
      
      // Fase 2: Carga constante
      constantUsersPerSec(50) during (2.minutes),
      
      // Fase 3: Pico de tráfico
      rampUsers(300) during (30.seconds),
      
      // Fase 4: Sostenimiento del pico
      constantUsersPerSec(100) during (1.minute),
      
      // Fase 5: Descenso gradual
      rampUsers(50) during (1.minute)
    ).protocols(httpProtocol)
  ).maxDuration(10.minutes)
   .assertions(
     global.responseTime.percentile3.lt(5000), // 99th percentile < 5s
     global.successfulRequests.percent.gt(90)  // 90% de éxito mínimo
   )
}
