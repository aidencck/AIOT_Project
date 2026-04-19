#!/bin/bash
echo "Packaging all modules..."
mvn clean package -DskipTests

echo "Starting Gateway Service..."
nohup java -jar aiot-gateway/target/aiot-gateway-1.0.0-SNAPSHOT.jar > aiot-gateway.log 2>&1 &
echo $! > gateway.pid

echo "Starting Device Service..."
nohup java -jar aiot-device-service/target/aiot-device-service-1.0.0-SNAPSHOT.jar > aiot-device-service.log 2>&1 &
echo $! > device.pid

echo "Starting Auth Service..."
nohup java -jar aiot-auth-service/target/aiot-auth-service-1.0.0-SNAPSHOT.jar > aiot-auth-service.log 2>&1 &
echo $! > auth.pid

echo "Waiting for services to register with Nacos (30 seconds)..."
sleep 30
echo "Services started."
