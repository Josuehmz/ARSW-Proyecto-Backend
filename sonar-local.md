# Ejecutar Sonar Localmente

## Opción 1: Con SonarCloud (si tienes cuenta)

1. Obtén tu token de SonarCloud:
   - Ve a https://sonarcloud.io
   - Inicia sesión con GitHub
   - Ve a "My Account" > "Security"
   - Genera un token

2. Ejecuta el análisis:
```bash
mvn clean verify
mvn sonar:sonar \
  -Dsonar.projectKey=Arsw-Balatro \
  -Dsonar.projectName='Arsw Balatro' \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.login=TU_TOKEN_AQUI
```

## Opción 2: Con SonarQube local (si tienes servidor propio)

```bash
mvn clean verify
mvn sonar:sonar \
  -Dsonar.projectKey=Arsw-Balatro \
  -Dsonar.projectName='Arsw Balatro' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=TU_TOKEN_AQUI
```

## Opción 3: Ver solo el reporte de cobertura (sin servidor Sonar)

El reporte de JaCoCo se genera automáticamente en:
```
target/site/jacoco/index.html
```

Abre este archivo en tu navegador para ver la cobertura de código.

