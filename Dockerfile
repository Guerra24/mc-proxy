FROM eclipse-temurin:17-alpine
COPY build/install/mc-proxy /mc-proxy
WORKDIR /mc-proxy
CMD ["/mc-proxy/bin/mc-proxy"]