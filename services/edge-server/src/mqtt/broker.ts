import Aedes from "aedes";
import net from "node:net";
import http from "node:http";
import websocketStream from "websocket-stream";
import pino from "pino";
import { config } from "../config.js";

const logger = pino({ name: "mqtt-broker", level: config.logLevel });

export interface MqttBrokerInstance {
  aedes: Aedes;
  tcpServer: net.Server;
  wsServer: http.Server;
  close: () => Promise<void>;
}

export function createBroker(): MqttBrokerInstance {
  const aedes = new Aedes();
  const tripPrefix = `rb/${config.tripId}`;

  // ACL - authorizePublish
  aedes.authorizePublish = (client, packet, callback) => {
    if (!client) {
      // Internal server publish
      return callback(null);
    }

    const clientId = client.id;
    const topic = packet.topic;

    // Server client
    if (clientId.startsWith("s-") || clientId === "server") {
      return callback(null);
    }

    // Passenger: can only publish to orders/new and complaints/new
    if (clientId.startsWith("p-")) {
      if (topic === `${tripPrefix}/orders/new` || topic === `${tripPrefix}/complaints/new`) {
        return callback(null);
      }
      logger.warn({ clientId, topic }, "Unauthorized publish attempt by passenger");
      return callback(new Error("Unauthorized publish topic for passenger"));
    }

    // Captain: can only publish to captain/{captainId}/actions
    if (clientId.startsWith("c-")) {
      const captainId = clientId.slice(2);
      if (topic === `${tripPrefix}/captain/${captainId}/actions`) {
        return callback(null);
      }
      logger.warn({ clientId, topic }, "Unauthorized publish attempt by captain");
      return callback(new Error("Unauthorized publish topic for captain"));
    }

    // Kitchen: can publish to kitchen/actions
    if (clientId.startsWith("k-")) {
      if (topic === `${tripPrefix}/kitchen/actions`) {
        return callback(null);
      }
      logger.warn({ clientId, topic }, "Unauthorized publish attempt by kitchen");
      return callback(new Error("Unauthorized publish topic for kitchen"));
    }

    // Default allow for standard dev/testing clients unless explicitly forbidden
    callback(null);
  };

  // ACL - authorizeSubscribe
  aedes.authorizeSubscribe = (client, subscription, callback) => {
    if (!client) return callback(null, subscription);

    const clientId = client.id;
    const topic = subscription.topic;

    // Server client
    if (clientId.startsWith("s-") || clientId === "server") {
      return callback(null, subscription);
    }

    // Passenger: can subscribe to orders/{orderId}/status, complaints/{complaintId}/status
    if (clientId.startsWith("p-")) {
      if (
        topic.startsWith(`${tripPrefix}/orders/`) ||
        topic.startsWith(`${tripPrefix}/complaints/`)
      ) {
        return callback(null, subscription);
      }
      logger.warn({ clientId, topic }, "Unauthorized subscribe attempt by passenger");
      return callback(new Error("Unauthorized subscribe topic for passenger"), null);
    }

    // Captain: can subscribe to captain/{captainId}/alerts
    if (clientId.startsWith("c-")) {
      const captainId = clientId.slice(2);
      if (
        topic === `${tripPrefix}/captain/${captainId}/alerts` ||
        topic === `${tripPrefix}/captain/+/alerts`
      ) {
        return callback(null, subscription);
      }
      logger.warn({ clientId, topic }, "Unauthorized subscribe attempt by captain");
      return callback(new Error("Unauthorized subscribe topic for captain"), null);
    }

    // Kitchen: can subscribe to kitchen/queue
    if (clientId.startsWith("k-")) {
      if (topic === `${tripPrefix}/kitchen/queue`) {
        return callback(null, subscription);
      }
      logger.warn({ clientId, topic }, "Unauthorized subscribe attempt by kitchen");
      return callback(new Error("Unauthorized subscribe topic for kitchen"), null);
    }

    callback(null, subscription);
  };

  // TCP MQTT Server
  const tcpServer = net.createServer(aedes.handle);

  // WebSocket MQTT Server
  const wsServer = http.createServer();
  websocketStream.createServer({ server: wsServer }, aedes.handle as any);

  const close = async () => {
    return new Promise<void>((resolve) => {
      aedes.close(() => {
        tcpServer.close(() => {
          wsServer.close(() => {
            resolve();
          });
        });
      });
    });
  };

  return { aedes, tcpServer, wsServer, close };
}
