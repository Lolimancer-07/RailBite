import { NativeModules } from "react-native";

const WifiBinder = NativeModules?.WifiBinder || {
  connectToPantry(_ssid: string, _passphrase?: string): boolean {
    return false;
  },
  unbind(): void {},
  isBound(): boolean {
    return false;
  },
};

export function connectToPantry(ssid: string = "RailBite_Pantry", passphrase?: string): boolean {
  try {
    return WifiBinder.connectToPantry(ssid, passphrase);
  } catch {
    return false;
  }
}

export function unbindPantry(): void {
  try {
    WifiBinder.unbind();
  } catch {}
}

export function isPantryBound(): boolean {
  try {
    return WifiBinder.isBound();
  } catch {
    return false;
  }
}

export function startAutoReconnectLoop(
  ssid: string = "RailBite_Pantry",
  passphrase?: string,
  intervalMs: number = 10000,
): () => void {
  let timer: any = null;

  const tryConnect = () => {
    if (!isPantryBound()) {
      connectToPantry(ssid, passphrase);
    }
  };

  tryConnect();
  timer = setInterval(tryConnect, intervalMs);

  return () => {
    if (timer) clearInterval(timer);
    unbindPantry();
  };
}
