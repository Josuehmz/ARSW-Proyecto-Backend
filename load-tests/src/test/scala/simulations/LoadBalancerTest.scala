package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Prueba de balanceo de carga
 * Verifica que las peticiones se distribuyen correctamente entre las instancias EC2
 */
class LoadBalancerTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://balatro-alb-1252182129.us-east-1.elb.amazonaws.com")
    .acceptHeader("application/json")
    .disableFollowRedirect // No seguir redirects automáticamente

  // Escenario que hace múltiples peticiones consecutivas
  val lbScenario = scenario("Load Balancer Distribution Test")
    .repeat(10) {
      exec(
        http("Health Check ${repetition}")
          .get("/actuator/health")
          .check(status.is(200))
          .check(jsonPath("$.status").is("UP"))
          .check(header("Server").saveAs("serverHeader")) // Capturar info del servidor
      )
      .pause(500.milliseconds)
    }

  setUp(
    lbScenario.inject(
      atOnceUsers(20), // 20 usuarios simultáneos
      rampUsers(50) during (1.minute)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.mean.lt(1000), // Tiempo medio < 1s
     global.failedRequests.percent.lt(1) // Menos del 1% de fallos
   )
}
