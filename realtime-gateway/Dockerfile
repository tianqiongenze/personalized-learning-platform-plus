FROM 10.167.2.175:30500/base/golang:1.26-alpine AS build
WORKDIR /src
ENV GOPROXY=https://goproxy.cn,direct
COPY go.mod ./
RUN go mod download
COPY main.go .
RUN CGO_ENABLED=0 GOFLAGS=-mod=mod go build -ldflags="-s -w" -o /out/realtime-gateway .

FROM 10.167.2.175:30500/base/distroless-static:nonroot
COPY --from=build /out/realtime-gateway /realtime-gateway
EXPOSE 8095
ENTRYPOINT ["/realtime-gateway"]
