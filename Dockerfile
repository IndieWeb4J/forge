FROM gcr.io/distroless/java25-debian13

WORKDIR /app
COPY forge.jar /app/forge.jar

EXPOSE 8080
CMD ["/app/forge.jar"]
