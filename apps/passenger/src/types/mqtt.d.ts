declare module "mqtt" {
  export interface MqttClient {
    connected: boolean;
    on(event: string, callback: (...args: any[]) => void): this;
    subscribe(topic: string, opts?: any, callback?: (err?: Error) => void): this;
    publish(topic: string, message: string | Buffer, opts?: any, callback?: (err?: Error) => void): this;
    end(force?: boolean, opts?: any, callback?: () => void): this;
  }

  export interface IClientOptions {
    clientId?: string;
    reconnectPeriod?: number;
    connectTimeout?: number;
    [key: string]: any;
  }

  export function connect(brokerUrl: string, opts?: IClientOptions): MqttClient;
}

