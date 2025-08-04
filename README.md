This demo was created with usage of Mono/Flux stack and Reactive Repository.

The task could be solved via usual blocking RestTemplate and CrudRepository as well with some refactoring.

Requirements:
- java 21
- free local port 8081
- docker engine
- gradle

Build steps:
1. Run `docker-compose.yml` to run postrgesql container
2. run `gradle bootRun`

Swagger - http://localhost:8081/swagger-ui/index.html#
DB - jdbc:postgresql://localhost:5432/postgres