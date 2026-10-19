FROM 10.167.2.175:30500/base/rust:1.94-slim AS build
WORKDIR /src
COPY Cargo.toml ./
COPY src ./src
RUN cargo build --release

FROM 10.167.2.175:30500/base/distroless-cc:nonroot
COPY --from=build /src/target/release/vector-reranker /vector-reranker
EXPOSE 8090
ENTRYPOINT ["/vector-reranker"]
